package dev.novadev.novabank.storage.sql;

import dev.novadev.novabank.core.delivery.PendingDeliveryStore;
import dev.novadev.novabank.core.delivery.PendingDeliveryStoreContractTest;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;

/**
 * Contrat {@link PendingDeliveryStore} (livraisons en attente) applique au stockage SQL sur un vrai serveur MySQL ou
 * MariaDB : une base par classe, un prefixe de tables neuf (donc un schema neuf) par test.
 */
@RequiresSqlServer
class ServerPendingDeliveryStoreContractTest extends PendingDeliveryStoreContractTest {

    private static final AtomicInteger PREFIXES = new AtomicInteger();
    private static ServerDatabase database;

    private final List<SqlStorage> opened = new ArrayList<>();

    @BeforeAll
    static void createDatabase() throws SQLException {
        database = ServerDatabase.create();
    }

    @AfterAll
    static void dropDatabase() throws SQLException {
        database.close();
    }

    @Override
    protected PendingDeliveryStore newStore() {
        SqlStorage storage = database.open("c" + PREFIXES.incrementAndGet() + "_");
        opened.add(storage);
        return storage.deliveries();
    }

    @AfterEach
    void closeStorages() {
        opened.forEach(SqlStorage::close);
    }
}
