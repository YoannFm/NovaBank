package dev.novadev.novabank.storage.file;

import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.core.migration.MigrationReport;
import dev.novadev.novabank.core.migration.StorageMigration;
import dev.novadev.novabank.core.storage.StorageBackend;
import dev.novadev.novabank.core.testing.ContentFixture;
import dev.novadev.novabank.core.testing.ContentInvariants;
import dev.novadev.novabank.core.testing.InMemoryStorageBackend;
import dev.novadev.novabank.storage.sql.SqlStorage;
import dev.novadev.novabank.storage.sql.dialect.SqliteDialect;
import dev.novadev.novabank.storage.sql.pool.ConnectionPools;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Migrations reelles entre implementations : memoire -> SQLite -> JSON -> YAML -> SQLite. Chaque
 * cible est fermee puis rouverte avant de servir de source : la copie verifiee est bien celle
 * relue depuis le disque. Le contenu final est identique au contenu d'origine.
 */
class FileMigrationTest {

    @TempDir
    Path directory;

    @Test
    void memoire_sqlite_json_yaml_sqlite_sans_aucun_ecart() throws SQLException {
        InMemoryStorageBackend origin = new InMemoryStorageBackend();
        ContentFixture.populate(origin);
        List<String> reports = new ArrayList<>();
        List<Supplier<StorageBackend>> chain = List.of(
                sqlite(directory.resolve("premier.db")),
                () -> FileTestSupport.open(directory.resolve("json"), FileFormat.JSON),
                () -> FileTestSupport.open(directory.resolve("yaml"), FileFormat.YAML),
                sqlite(directory.resolve("retour.db")));

        StorageBackend source = origin;
        for (Supplier<StorageBackend> next : chain) {
            try (StorageBackend target = next.get()) {
                MigrationReport report = new StorageMigration(line -> { }, Clock.systemUTC())
                        .migrate(source.content(), target.content());
                reports.add(target.getClass().getSimpleName() + " : " + report.describe());
            }
            if (source != origin) {
                source.close();
            }
            source = next.get();
            ContentFixture.assertSameContent(origin.content(), source.content());
            assertThat(ContentInvariants.violations(source.content())).isEmpty();
        }
        source.close();
        assertThat(reports).hasSize(4);
        System.out.println(String.join(System.lineSeparator(), reports));
    }

    private static Supplier<StorageBackend> sqlite(Path file) throws SQLException {
        SqliteDialect dialect = new SqliteDialect();
        var driver = DriverManager.getDriver("jdbc:sqlite:");
        return () -> SqlStorage.open(ConnectionPools.sqlite(file, driver, dialect,
                ConnectionPools.DEFAULT_SQLITE_POOL_SIZE), dialect, SqlStorage.DEFAULT_TABLE_PREFIX, Clock.systemUTC());
    }
}
