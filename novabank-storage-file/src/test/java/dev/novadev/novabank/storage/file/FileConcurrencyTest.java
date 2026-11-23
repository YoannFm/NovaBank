package dev.novadev.novabank.storage.file;

import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.core.migration.StorageMigration;
import dev.novadev.novabank.core.testing.ContentFixture;
import dev.novadev.novabank.core.testing.ContentInvariants;
import dev.novadev.novabank.core.testing.InMemoryStorageBackend;
import dev.novadev.novabank.core.testing.LedgerConcurrencyScenario;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.io.TempDir;

/**
 * Concurrence reelle (16 threads, {@link LedgerConcurrencyScenario}) avec un seuil de compaction
 * minuscule : des dizaines de compactions ont lieu pendant l'activite. Le contenu relu apres
 * fermeture est identique et coherent.
 */
class FileConcurrencyTest {

    private static final int THREADS = 16;
    private static final int ROUNDS = 25;
    private static final long TINY_COMPACTION_BYTES = 8 * 1024;

    @TempDir
    Path directory;

    @ParameterizedTest
    @EnumSource(FileFormat.class)
    void seize_threads_et_compactions_pendant_l_activite_ne_perdent_ni_ne_dupliquent_rien(FileFormat format)
            throws Exception {
        InMemoryStorageBackend expected = new InMemoryStorageBackend();
        try (FileStorage storage = FileTestSupport.open(directory, format, TINY_COMPACTION_BYTES)) {
            Path snapshot = storage.snapshotFile();
            LedgerConcurrencyScenario.run(List.of(storage), THREADS, ROUNDS);
            ContentFixture.populate(storage);
            assertThat(snapshot).as("compactions pendant l'activite").exists();
            assertThat(Files.size(storage.journalFile())).isLessThan(TINY_COMPACTION_BYTES * 4);
            assertThat(ContentInvariants.violations(storage.content())).isEmpty();
            new StorageMigration(line -> { }, Clock.systemUTC()).migrate(storage.content(), expected.content());
        }
        try (FileStorage reopened = FileTestSupport.open(directory, format)) {
            ContentFixture.assertSameContent(expected.content(), reopened.content());
            assertThat(ContentInvariants.violations(reopened.content())).isEmpty();
        }
    }
}
