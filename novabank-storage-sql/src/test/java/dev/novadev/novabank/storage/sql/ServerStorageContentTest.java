package dev.novadev.novabank.storage.sql;

import dev.novadev.novabank.core.storage.StorageBackend;
import dev.novadev.novabank.core.storage.StorageContentContractTest;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;

/**
 * Contrat du contenu complet (migration) sur un vrai serveur MySQL ou MariaDB : un prefixe de
 * tables neuf (donc un schema neuf) par backend.
 */
@RequiresSqlServer
class ServerStorageContentTest extends StorageContentContractTest {

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
    protected StorageBackend newBackend() {
        SqlStorage storage = database.open("m" + PREFIXES.incrementAndGet() + "_");
        opened.add(storage);
        return storage;
    }

    @AfterEach
    void closeStorages() {
        opened.forEach(SqlStorage::close);
    }
}
