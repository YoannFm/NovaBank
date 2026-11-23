package dev.novadev.novabank.storage.sql;

import dev.novadev.novabank.core.delivery.PendingDeliveryStore;
import dev.novadev.novabank.core.delivery.PendingDeliveryStoreContractTest;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;

/**
 * Contrat {@link PendingDeliveryStore} applique au stockage SQL sur un fichier SQLite reel.
 */
class SqlitePendingDeliveryStoreContractTest extends PendingDeliveryStoreContractTest {

    @TempDir
    Path directory;

    private final List<SqlStorage> opened = new ArrayList<>();

    @Override
    protected PendingDeliveryStore newStore() {
        SqlStorage storage = SqliteTestSupport.open(directory.resolve("deliveries-" + UUID.randomUUID() + ".db"));
        opened.add(storage);
        return storage.deliveries();
    }

    @AfterEach
    void closeStorages() {
        opened.forEach(SqlStorage::close);
    }
}
