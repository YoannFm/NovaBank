package dev.novadev.novabank.storage.sql;

import dev.novadev.novabank.core.sync.BankChangeFeedContractTest;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;

/**
 * Contrat du flux de changements sur un fichier SQLite reel.
 */
class SqliteBankChangeFeedTest extends BankChangeFeedContractTest {

    @TempDir
    Path directory;

    private final List<SqlStorage> opened = new ArrayList<>();

    @Override
    protected FeedFixture newFixture() {
        SqlStorage storage = SqliteTestSupport.open(directory.resolve("feed-" + UUID.randomUUID() + ".db"));
        opened.add(storage);
        return new FeedFixture(storage.banks(), storage.changeFeed().orElseThrow());
    }

    @AfterEach
    void closeStorages() {
        opened.forEach(SqlStorage::close);
    }
}
