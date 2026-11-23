package dev.novadev.novabank.storage.sql;

import dev.novadev.novabank.core.sync.BankChangeFeedContractTest;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;

/**
 * Contrat du flux de changements sur un vrai serveur MySQL ou MariaDB.
 */
@RequiresSqlServer
class ServerBankChangeFeedTest extends BankChangeFeedContractTest {

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
    protected FeedFixture newFixture() {
        SqlStorage storage = database.open("f" + PREFIXES.incrementAndGet() + "_");
        opened.add(storage);
        return new FeedFixture(storage.banks(), storage.changeFeed().orElseThrow());
    }

    @AfterEach
    void closeStorages() {
        opened.forEach(SqlStorage::close);
    }
}
