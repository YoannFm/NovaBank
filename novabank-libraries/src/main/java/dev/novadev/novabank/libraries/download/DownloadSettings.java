package dev.novadev.novabank.libraries.download;

import dev.novadev.novabank.core.text.ConsoleText;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Reglages du telechargement des artefacts.
 *
 * <p>Les depots sont essayes dans l'ordre. Un miroir est sur meme s'il n'est pas de
 * confiance : tout contenu dont la somme SHA-256 differe de la somme epinglee est rejete.
 *
 * @param repositories          URL de base des depots Maven, {@code http} ou {@code https},
 *                              sans identifiants, requete ni fragment ; un {@code /} final est ajoute
 * @param connectTimeout        delai maximal d'etablissement d'une connexion
 * @param readTimeout           delai maximal sans recevoir de donnee (en-tetes ou contenu)
 * @param attemptsPerRepository nombre d'essais par depot pour les erreurs temporaires, de 1 a 10
 * @param retryDelay            pause entre deux essais sur un meme depot
 */
public record DownloadSettings(List<URI> repositories, Duration connectTimeout, Duration readTimeout,
                               int attemptsPerRepository, Duration retryDelay) {

    /** Maven Central. */
    public static final URI MAVEN_CENTRAL = URI.create("https://repo.maven.apache.org/maven2/");

    /** Nombre maximal d'essais par depot. */
    public static final int MAX_ATTEMPTS = 10;

    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(30);
    private static final int DEFAULT_ATTEMPTS = 3;
    private static final Duration DEFAULT_RETRY_DELAY = Duration.ofSeconds(2);

    /**
     * Valide les reglages et normalise les URL des depots.
     *
     * @throws NullPointerException     si un champ est nul
     * @throws IllegalArgumentException si un reglage est invalide
     */
    public DownloadSettings {
        Objects.requireNonNull(repositories, "repositories");
        if (repositories.isEmpty()) {
            throw new IllegalArgumentException(ConsoleText.tr("Aucun depot de telechargement"));
        }
        List<URI> normalized = new ArrayList<>(repositories.size());
        for (URI repository : repositories) {
            normalized.add(normalize(repository));
        }
        repositories = List.copyOf(normalized);
        requirePositive(connectTimeout, "connectTimeout");
        requirePositive(readTimeout, "readTimeout");
        Objects.requireNonNull(retryDelay, "retryDelay");
        if (retryDelay.isNegative()) {
            throw new IllegalArgumentException(ConsoleText.tr("Pause entre essais negative : {0}", retryDelay));
        }
        if (attemptsPerRepository < 1 || attemptsPerRepository > MAX_ATTEMPTS) {
            throw new IllegalArgumentException(ConsoleText.tr("Nombre d'essais hors bornes (1 a {0}) : {1}",
                    MAX_ATTEMPTS, attemptsPerRepository));
        }
    }

    /**
     * Reglages par defaut : Maven Central d'abord, puis les miroirs dans l'ordre donne ;
     * connexion 10 s, lecture 30 s, 3 essais par depot espaces de 2 s.
     *
     * @param mirrors miroirs de repli, eventuellement vide
     * @return les reglages
     */
    public static DownloadSettings defaults(List<URI> mirrors) {
        List<URI> repositories = new ArrayList<>();
        repositories.add(MAVEN_CENTRAL);
        repositories.addAll(mirrors);
        return new DownloadSettings(repositories, DEFAULT_CONNECT_TIMEOUT, DEFAULT_READ_TIMEOUT,
                DEFAULT_ATTEMPTS, DEFAULT_RETRY_DELAY);
    }

    private static URI normalize(URI repository) {
        Objects.requireNonNull(repository, "repository");
        String scheme = repository.getScheme() == null ? "" : repository.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("https") && !scheme.equals("http")) {
            throw new IllegalArgumentException(ConsoleText.tr("Depot non HTTP(S) : {0}", repository));
        }
        if (repository.getHost() == null || repository.getRawUserInfo() != null
                || repository.getRawQuery() != null || repository.getRawFragment() != null) {
            throw new IllegalArgumentException(
                    ConsoleText.tr("Depot invalide (hote requis ; identifiants, requete et fragment interdits) : {0}",
                            repository));
        }
        String text = repository.toString();
        return text.endsWith("/") ? repository : URI.create(text + '/');
    }

    private static void requirePositive(Duration duration, String name) {
        Objects.requireNonNull(duration, name);
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException(ConsoleText.tr("Delai {0} invalide : {1}", name, duration));
        }
    }
}
