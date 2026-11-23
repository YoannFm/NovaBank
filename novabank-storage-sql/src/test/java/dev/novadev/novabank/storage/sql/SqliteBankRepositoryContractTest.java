package dev.novadev.novabank.storage.sql;

import dev.novadev.novabank.core.repository.BankRepository;
import dev.novadev.novabank.core.repository.BankRepositoryContractTest;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;

/**
 * Contrat {@link BankRepository} applique au stockage SQL sur un fichier SQLite reel,
 * un fichier neuf par test.
 */
class SqliteBankRepositoryContractTest extends BankRepositoryContractTest {

    @TempDir
    Path directory;

    private final List<SqlStorage> opened = new ArrayList<>();

    @Override
    protected BankRepository newRepository() {
        SqlStorage storage = SqliteTestSupport.open(directory.resolve("banks-" + UUID.randomUUID() + ".db"));
        opened.add(storage);
        return storage.banks();
    }

    @AfterEach
    void closeStorages() {
        opened.forEach(SqlStorage::close);
    }
}
