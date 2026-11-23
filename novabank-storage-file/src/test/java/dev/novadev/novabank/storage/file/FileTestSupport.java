package dev.novadev.novabank.storage.file;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.logging.Logger;

/**
 * Ouverture de stockages fichier reels pour les tests.
 */
final class FileTestSupport {

    static final Logger LOGGER = Logger.getLogger("NovaBank-test-file");

    private FileTestSupport() {
    }

    static FileStorage open(Path directory, FileFormat format) {
        return FileStorage.open(FileStorageOptions.of(directory, format, Duration.ofHours(1), Clock.systemUTC(),
                LOGGER));
    }

    static FileStorage open(Path directory, FileFormat format, long compactionBytes) {
        return FileStorage.open(new FileStorageOptions(directory, format, compactionBytes, Duration.ofHours(1),
                Clock.systemUTC(), LOGGER));
    }
}
