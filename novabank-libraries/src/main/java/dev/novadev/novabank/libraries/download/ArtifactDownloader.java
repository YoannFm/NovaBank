package dev.novadev.novabank.libraries.download;

import dev.novadev.novabank.core.text.ConsoleText;
import dev.novadev.novabank.libraries.LibraryException;
import dev.novadev.novabank.libraries.LibraryLogger;
import dev.novadev.novabank.libraries.artifact.LibraryArtifact;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Telecharge un artefact epingle dans un fichier, depot apres depot.
 *
 * <p>Pour chaque depot, dans l'ordre des reglages : jusqu'a
 * {@link DownloadSettings#attemptsPerRepository()} essais pour les erreurs temporaires
 * (reseau, delai depasse, HTTP 5xx, 408, 429). Une erreur definitive pour ce depot
 * (HTTP 404 ou autre 4xx, fichier trop gros, somme SHA-256 invalide) fait passer
 * directement au depot suivant. Le fichier n'est declare valide que si sa taille et sa
 * somme SHA-256 sont exactement celles epinglees.
 *
 * <p>Le client HTTP du JDK n'est cree qu'au premier telechargement : aucun acces reseau ni
 * thread si tout est deja en cache. Il n'est pas ferme (methode absente en Java 17) ; ses
 * threads sont des demons liberes avec lui.
 */
public final class ArtifactDownloader {

    private static final String USER_AGENT = "NovaBank-Libraries";
    private static final long MAX_POLL_MILLIS = 250L;
    private static final double MEBIBYTE = 1024.0 * 1024.0;

    private final DownloadSettings settings;
    private final LibraryLogger logger;
    private HttpClient client;

    /**
     * @param settings reglages du telechargement
     * @param logger   journal des essais
     */
    public ArtifactDownloader(DownloadSettings settings, LibraryLogger logger) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Telecharge l'artefact dans {@code destination} (cree ou ecrase). Au retour, le
     * fichier contient exactement l'artefact epingle ; en cas d'echec son contenu est
     * indetermine et l'appelant doit le supprimer.
     *
     * @param artifact    artefact epingle
     * @param destination fichier temporaire de reception
     * @throws LibraryException si aucun depot n'a fourni l'artefact, avec la cause de chaque echec
     */
    public void download(LibraryArtifact artifact, Path destination) {
        Objects.requireNonNull(artifact, "artifact");
        Objects.requireNonNull(destination, "destination");
        List<String> failures = new ArrayList<>();
        for (URI repository : settings.repositories()) {
            URI uri = repository.resolve(artifact.repositoryPath());
            logger.info(ConsoleText.tr("Telechargement de {0} ({1} Mo) depuis {2}",
                    artifact.coordinates(), megabytes(artifact.size()), repository));
            for (int attempt = 1; attempt <= settings.attemptsPerRepository(); attempt++) {
                try {
                    fetch(uri, artifact, destination);
                    logger.info(ConsoleText.tr("{0} telecharge, somme SHA-256 verifiee", artifact.coordinates()));
                    return;
                } catch (AttemptFailure failure) {
                    String detail = repository + " essai " + attempt + " : " + failure.getMessage();
                    failures.add(detail);
                    logger.warning(ConsoleText.tr("Echec du telechargement de {0} depuis {1}",
                            artifact.coordinates(), detail));
                    if (!failure.retryable()) {
                        break;
                    }
                    if (attempt < settings.attemptsPerRepository()) {
                        pause();
                    }
                }
            }
        }
        throw new LibraryException(ConsoleText.tr("Impossible d'obtenir {0} ; echecs : {1}",
                artifact.coordinates(), String.join(" ; ", failures)));
    }

    private void fetch(URI uri, LibraryArtifact artifact, Path destination) throws AttemptFailure {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .header("User-Agent", USER_AGENT)
                .timeout(settings.readTimeout())
                .build();
        try (FileChannel channel = FileChannel.open(destination, StandardOpenOption.CREATE,
                StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            ArtifactBodySubscriber body = new ArtifactBodySubscriber(artifact, channel);
            await(client().sendAsync(request, body::accept), body);
            body.verifyComplete();
        } catch (IOException fileFailure) {
            throw new AttemptFailure("fichier temporaire inutilisable : " + fileFailure, false);
        }
    }

    /**
     * Attend la fin du transfert ; l'abandonne si aucune donnee n'arrive pendant le delai de
     * lecture (le delai de la requete ne couvre que l'attente des en-tetes).
     */
    private void await(CompletableFuture<HttpResponse<Void>> response, ArtifactBodySubscriber body)
            throws AttemptFailure {
        long readTimeoutNanos = settings.readTimeout().toNanos();
        long pollMillis = Math.max(1L, Math.min(MAX_POLL_MILLIS, settings.readTimeout().toMillis() / 4));
        while (true) {
            try {
                response.get(pollMillis, TimeUnit.MILLISECONDS);
                return;
            } catch (TimeoutException stillRunning) {
                if (body.idleNanos() >= readTimeoutNanos) {
                    body.fail(new AttemptFailure("delai de lecture depasse (aucune donnee depuis "
                            + settings.readTimeout().toMillis() + " ms)", true));
                    response.cancel(true);
                    throw body.failure().orElseThrow();
                }
            } catch (ExecutionException failed) {
                throw body.failure().orElseGet(() -> describe(failed.getCause()));
            } catch (InterruptedException interrupted) {
                response.cancel(true);
                Thread.currentThread().interrupt();
                throw new LibraryException(ConsoleText.tr("Telechargement interrompu"), interrupted);
            }
        }
    }

    private static AttemptFailure describe(Throwable cause) {
        if (cause instanceof HttpConnectTimeoutException) {
            return new AttemptFailure("delai de connexion depasse", true);
        }
        if (cause instanceof HttpTimeoutException) {
            return new AttemptFailure("delai de reponse depasse", true);
        }
        if (cause instanceof ConnectException) {
            return new AttemptFailure("connexion impossible", true);
        }
        return new AttemptFailure("erreur reseau : " + cause, true);
    }

    private void pause() {
        try {
            Thread.sleep(settings.retryDelay().toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new LibraryException(ConsoleText.tr("Telechargement interrompu"), interrupted);
        }
    }

    private synchronized HttpClient client() {
        if (client == null) {
            client = HttpClient.newBuilder()
                    .connectTimeout(settings.connectTimeout())
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
        }
        return client;
    }

    private static String megabytes(long bytes) {
        return String.format(Locale.ROOT, "%.1f", bytes / MEBIBYTE);
    }
}
