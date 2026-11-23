package dev.novadev.novabank.storage.sql;

import dev.novadev.novabank.core.testing.LedgerConcurrencyScenario;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Concurrence reelle sur SQLite : {@link LedgerConcurrencyScenario} avec 16 threads, sur un pool
 * puis sur deux pools ouverts sur le meme fichier.
 */
class SqliteConcurrencyTest {

    private static final int THREADS = 16;
    private static final int ROUNDS = 25;

    @TempDir
    Path directory;

    @Test
    void seize_threads_sur_un_pool_ne_perdent_ni_ne_dupliquent_rien() throws Exception {
        Path file = directory.resolve("concurrency.db");
        try (SqlStorage storage = SqliteTestSupport.open(file)) {
            LedgerConcurrencyScenario.run(List.of(storage), THREADS, ROUNDS);
        }
        JournalInvariants.assertConsistent(() -> SqliteTestSupport.rawConnection(file),
                SqlStorage.DEFAULT_TABLE_PREFIX);
    }

    @Test
    void deux_pools_sur_le_meme_fichier_se_serialisent_par_le_verrou_sqlite() throws Exception {
        Path file = directory.resolve("two-pools.db");
        try (SqlStorage first = SqliteTestSupport.open(file); SqlStorage second = SqliteTestSupport.open(file)) {
            LedgerConcurrencyScenario.run(List.of(first, second), THREADS, ROUNDS);
        }
        JournalInvariants.assertConsistent(() -> SqliteTestSupport.rawConnection(file),
                SqlStorage.DEFAULT_TABLE_PREFIX);
    }
}
