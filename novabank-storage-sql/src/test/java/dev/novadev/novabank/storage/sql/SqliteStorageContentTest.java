package dev.novadev.novabank.storage.sql;

import dev.novadev.novabank.core.storage.StorageBackend;
import dev.novadev.novabank.core.storage.StorageContentContractTest;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;

/**
 * Contrat du contenu complet (migration) applique a SQLite, un fichier neuf par backend.
 */
class SqliteStorageContentTest extends StorageContentContractTest {

    @TempDir
    Path directory;

    private final List<SqlStorage> opened = new ArrayList<>();

    @Override
    protected StorageBackend newBackend() {
        SqlStorage storage = SqliteTestSupport.open(directory.resolve("content-" + UUID.randomUUID() + ".db"));
        opened.add(storage);
        return storage;
    }

    @AfterEach
    void closeStorages() {
        opened.forEach(SqlStorage::close);
    }
}
