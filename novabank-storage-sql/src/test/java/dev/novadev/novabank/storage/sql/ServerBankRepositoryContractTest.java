package dev.novadev.novabank.storage.sql;

import dev.novadev.novabank.core.repository.BankRepository;
import dev.novadev.novabank.core.repository.BankRepositoryContractTest;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;

/**
 * Contrat {@link BankRepository} (banques et journal) applique au stockage SQL sur un vrai serveur MySQL ou
 * MariaDB : une base par classe, un prefixe de tables neuf (donc un schema neuf) par test.
 */
@RequiresSqlServer
class ServerBankRepositoryContractTest extends BankRepositoryContractTest {

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
    protected BankRepository newRepository() {
        SqlStorage storage = database.open("c" + PREFIXES.incrementAndGet() + "_");
        opened.add(storage);
        return storage.banks();
    }

    @AfterEach
    void closeStorages() {
        opened.forEach(SqlStorage::close);
    }
}
