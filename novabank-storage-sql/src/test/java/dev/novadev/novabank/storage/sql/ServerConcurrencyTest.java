package dev.novadev.novabank.storage.sql;

import dev.novadev.novabank.core.testing.ConcurrentRunner;
import dev.novadev.novabank.core.testing.LedgerConcurrencyScenario;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Concurrence multi-serveurs simulee sur un vrai serveur MySQL / MariaDB : plusieurs pools
 * distincts (un pool = un serveur Minecraft, aucun verrou Java commun) et plusieurs threads par
 * pool executent {@link LedgerConcurrencyScenario} sur la meme banque : verrou de ligne
 * {@code FOR UPDATE}, version de banque et cle primaire du journal sont les seuls garde-fous.
 */
@RequiresSqlServer
class ServerConcurrencyTest {

    private static final int THREADS_PER_POOL = 4;
    private static final int ROUNDS = 30;
    private static final String PREFIX = "novabank_";

    private ServerDatabase database;
    private final List<SqlStorage> storages = new ArrayList<>();

    @BeforeEach
    void createDatabase() throws SQLException {
        database = ServerDatabase.create();
    }

    @AfterEach
    void dropDatabase() throws SQLException {
        storages.forEach(SqlStorage::close);
        database.close();
    }

    @ParameterizedTest(name = "{0} pools x " + THREADS_PER_POOL + " threads")
    @ValueSource(ints = {2, 4})
    void plusieurs_serveurs_sur_la_meme_banque_ne_perdent_ni_ne_dupliquent_rien(int pools) throws Exception {
        openPools(pools);

        LedgerConcurrencyScenario.run(storages, pools * THREADS_PER_POOL, ROUNDS);

        JournalInvariants.assertConsistent(database::rawConnection, PREFIX);
    }

    @Test
    void deux_banques_en_parallele_sur_trois_serveurs_restent_coherentes() throws Exception {
        openPools(3);

        ConcurrentRunner.run(2, scenario -> LedgerConcurrencyScenario.run(storages, 3 * THREADS_PER_POOL, ROUNDS));

        JournalInvariants.assertConsistent(database::rawConnection, PREFIX);
    }

    private void openPools(int count) {
        for (int i = 0; i < count; i++) {
            storages.add(database.open(PREFIX));
        }
    }
}
