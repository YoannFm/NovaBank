package dev.novadev.novabank.libraries.cache;

import dev.novadev.novabank.core.text.ConsoleText;
import dev.novadev.novabank.libraries.LibraryException;
import dev.novadev.novabank.libraries.LibraryLogger;
import dev.novadev.novabank.libraries.artifact.ArtifactChecksums;
import dev.novadev.novabank.libraries.artifact.LibraryArtifact;
import dev.novadev.novabank.libraries.download.ArtifactDownloader;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Dossier local des artefacts epingles ({@code plugins/NovaBank/libs/}).
 *
 * <p>Un artefact deja present est re-verifie (taille + SHA-256) a chaque demande : un
 * fichier corrompu ou remplace est retelecharge, jamais rendu. Un telechargement est ecrit
 * dans un fichier temporaire unique {@code <nom>.jar.<aleatoire>.part}, verifie pendant le
 * transfert, puis renomme atomiquement vers le nom final : le nom final ne designe jamais
 * un fichier partiel ou non verifie. Le fichier temporaire est supprime en cas d'echec.
 *
 * <p>Dans un meme processus, les demandes d'un meme artefact sont serialisees (un seul
 * telechargement). Entre processus partageant le dossier, chaque processus telecharge dans
 * son propre fichier temporaire et le renommage atomique garde un fichier final complet.
 */
public final class LocalLibraryCache {

    /** Age a partir duquel un fichier temporaire est considere abandonne (arret brutal). */
    static final Duration STALE_PART_AGE = Duration.ofHours(1);

    private static final String PART_SUFFIX = ".part";

    private final Path directory;
    private final ArtifactDownloader downloader;
    private final LibraryLogger logger;
    private final ConcurrentMap<String, Object> locks = new ConcurrentHashMap<>();

    /**
     * @param directory  dossier des bibliotheques (cree au besoin)
     * @param downloader telechargeur des artefacts absents ou corrompus
     * @param logger     journal
     */
    public LocalLibraryCache(Path directory, ArtifactDownloader downloader, LibraryLogger logger) {
        this.directory = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
        this.downloader = Objects.requireNonNull(downloader, "downloader");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * @return le dossier des bibliotheques (absolu)
     */
    public Path directory() {
        return directory;
    }

    /**
     * Rend le chemin d'un artefact verifie, en le telechargeant s'il est absent ou corrompu.
     *
     * @param artifact artefact epingle
     * @return le jar local, intact au moment de la verification
     * @throws LibraryException si l'artefact ne peut pas etre obtenu
     */
    public Path resolve(LibraryArtifact artifact) {
        Objects.requireNonNull(artifact, "artifact");
        synchronized (locks.computeIfAbsent(artifact.coordinates(), key -> new Object())) {
            try {
                Files.createDirectories(directory);
                Path target = directory.resolve(artifact.fileName());
                if (Files.exists(target)) {
                    if (ArtifactChecksums.isIntact(artifact, target)) {
                        return target;
                    }
                    logger.warning(ConsoleText.tr("Fichier {0} corrompu ou modifie : retelechargement",
                            target.getFileName()));
                }
                deleteStaleParts(artifact);
                download(artifact, target);
                return target;
            } catch (IOException failure) {
                throw new LibraryException(ConsoleText.tr("Dossier des bibliotheques inutilisable ({0}) : {1}",
                        directory, failure), failure);
            }
        }
    }

    /**
     * @param artifacts artefacts epingles
     * @return les jars locaux verifies, dans le meme ordre
     * @throws LibraryException si un artefact ne peut pas etre obtenu
     */
    public List<Path> resolveAll(List<LibraryArtifact> artifacts) {
        List<Path> jars = new ArrayList<>(artifacts.size());
        for (LibraryArtifact artifact : artifacts) {
            jars.add(resolve(artifact));
        }
        return jars;
    }

    private void download(LibraryArtifact artifact, Path target) throws IOException {
        Path part = Files.createTempFile(directory, artifact.fileName() + '.', PART_SUFFIX);
        try {
            downloader.download(artifact, part);
            moveIntoPlace(part, target);
        } finally {
            // Sans effet apres un renommage reussi ; supprime le fichier partiel sinon.
            Files.deleteIfExists(part);
        }
    }

    private static void moveIntoPlace(Path part, Path target) throws IOException {
        try {
            Files.move(part, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Supprime les fichiers temporaires de cet artefact laisses par un arret brutal. Les
     * fichiers recents sont conserves : ils peuvent appartenir a un autre processus en
     * cours de telechargement.
     */
    private void deleteStaleParts(LibraryArtifact artifact) throws IOException {
        Instant limit = Instant.now().minus(STALE_PART_AGE);
        try (DirectoryStream<Path> parts = Files.newDirectoryStream(directory,
                artifact.fileName() + ".*" + PART_SUFFIX)) {
            for (Path part : parts) {
                if (Files.getLastModifiedTime(part).toInstant().isBefore(limit)) {
                    Files.deleteIfExists(part);
                }
            }
        }
    }
}
