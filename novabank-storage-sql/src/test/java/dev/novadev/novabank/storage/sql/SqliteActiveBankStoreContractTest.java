package dev.novadev.novabank.storage.sql;

import dev.novadev.novabank.core.selection.ActiveBankStore;
import dev.novadev.novabank.core.selection.ActiveBankStoreContractTest;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;

/**
 * Contrat {@link ActiveBankStore} applique au stockage SQL sur un fichier SQLite reel.
 */
class SqliteActiveBankStoreContractTest extends ActiveBankStoreContractTest {

    @TempDir
    Path directory;

    private final List<SqlStorage> opened = new ArrayList<>();

    @Override
    protected ActiveBankStore newStore() {
        SqlStorage storage = SqliteTestSupport.open(directory.resolve("active-" + UUID.randomUUID() + ".db"));
        opened.add(storage);
        return storage.activeBanks();
    }

    @AfterEach
    void closeStorages() {
        opened.forEach(SqlStorage::close);
    }
}
