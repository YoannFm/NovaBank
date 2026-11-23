package dev.novadev.novabank.storage.mongo;

import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.core.migration.MigrationReport;
import dev.novadev.novabank.core.migration.StorageMigration;
import dev.novadev.novabank.core.storage.StorageBackend;
import dev.novadev.novabank.core.testing.ContentFixture;
import dev.novadev.novabank.core.testing.ContentInvariants;
import dev.novadev.novabank.storage.sql.SqlStorage;
import dev.novadev.novabank.storage.sql.dialect.SqliteDialect;
import dev.novadev.novabank.storage.sql.pool.ConnectionPools;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Clock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Migration reelle SQLite -> MongoDB (base neuve) puis retour vers un nouveau fichier SQLite :
 * aucun ecart, journal coherent, aucune operation en attente dans les banques copiees.
 */
@RequiresMongoServer
class MongoMigrationTest {

    @TempDir
    Path directory;

    private final MongoTestServer database = MongoTestServer.create();

    @AfterEach
    void dropDatabase() {
        database.close();
    }

    @Test
    void sqlite_vers_mongodb_puis_retour_sans_aucun_ecart() throws SQLException {
        try (SqlStorage original = sqlite(directory.resolve("original.db"))) {
            ContentFixture.populate(original);
            try (MongoBackend mongo = database.open()) {
                report("SQLite -> MongoDB", migrate(original, mongo));
            }
            try (MongoBackend mongo = database.open(); SqlStorage back = sqlite(directory.resolve("retour.db"))) {
                ContentFixture.assertSameContent(original.content(), mongo.content());
                assertThat(ContentInvariants.violations(mongo.content())).isEmpty();
                report("MongoDB -> SQLite", migrate(mongo, back));
            }
            assertThat(MongoJournalInvariants.violations(database.database())).isEmpty();
            try (SqlStorage back = sqlite(directory.resolve("retour.db"))) {
                ContentFixture.assertSameContent(original.content(), back.content());
            }
        }
    }

    private static MigrationReport migrate(StorageBackend source, StorageBackend target) {
        return new StorageMigration(line -> { }, Clock.systemUTC()).migrate(source.content(), target.content());
    }

    private static void report(String direction, MigrationReport report) {
        System.out.println("MIGRATION " + direction + " (" + (MongoTestServer.replicaSet() ? "replica set" : "autonome")
                + ") : " + report.describe());
    }

    private static SqlStorage sqlite(Path file) throws SQLException {
        SqliteDialect dialect = new SqliteDialect();
        return SqlStorage.open(ConnectionPools.sqlite(file, DriverManager.getDriver("jdbc:sqlite:"), dialect,
                ConnectionPools.DEFAULT_SQLITE_POOL_SIZE), dialect, SqlStorage.DEFAULT_TABLE_PREFIX, Clock.systemUTC());
    }
}
