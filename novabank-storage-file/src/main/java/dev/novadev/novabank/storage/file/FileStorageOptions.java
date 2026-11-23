package dev.novadev.novabank.storage.file;

import dev.novadev.novabank.core.text.ConsoleText;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Reglages d'un stockage fichier.
 *
 * @param directory          dossier des donnees (cree si absent)
 * @param format             format de l'instantane
 * @param compactionBytes    taille du journal d'ecriture qui declenche une compaction
 * @param compactionInterval anciennete maximale d'un journal non vide avant compaction
 * @param clock              horloge des dates techniques
 * @param logger             journal du serveur
 */
public record FileStorageOptions(Path directory, FileFormat format, long compactionBytes,
                                 Duration compactionInterval, Clock clock, Logger logger) {

    /** Taille du journal qui declenche une compaction, par defaut : 4 Mio. */
    public static final long DEFAULT_COMPACTION_BYTES = 4L * 1024 * 1024;

    /**
     * @throws IllegalArgumentException si un seuil n'est pas positif
     */
    public FileStorageOptions {
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(compactionInterval, "compactionInterval");
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(logger, "logger");
        if (compactionBytes < 1 || compactionInterval.isNegative() || compactionInterval.isZero()) {
            throw new IllegalArgumentException(ConsoleText.tr("Seuils de compaction invalides : {0} octets, {1}",
                    compactionBytes, compactionInterval));
        }
    }

    /**
     * @param directory          dossier des donnees
     * @param format             format de l'instantane
     * @param compactionInterval anciennete maximale du journal
     * @param clock              horloge
     * @param logger             journal du serveur
     * @return des reglages avec le seuil de taille par defaut
     */
    public static FileStorageOptions of(Path directory, FileFormat format, Duration compactionInterval, Clock clock,
                                        Logger logger) {
        return new FileStorageOptions(directory, format, DEFAULT_COMPACTION_BYTES, compactionInterval, clock, logger);
    }
}
