package dev.novadev.novabank.storage.sql;

import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.core.migration.MigrationReport;
import dev.novadev.novabank.core.migration.StorageMigration;
import dev.novadev.novabank.core.testing.ContentFixture;
import dev.novadev.novabank.core.testing.ContentInvariants;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Migration reelle SQLite -> serveur MySQL/MariaDB puis retour vers un nouveau fichier SQLite :
 * aucun ecart entre l'original et le contenu revenu (chaque cible fermee puis rouverte).
 */
@RequiresSqlServer
class ServerMigrationTest {

    private static ServerDatabase database;

    @TempDir
    Path directory;

    @BeforeAll
    static void createDatabase() throws SQLException {
        database = ServerDatabase.create();
    }

    @AfterAll
    static void dropDatabase() throws SQLException {
        database.close();
    }

    @Test
    void sqlite_vers_serveur_puis_retour_sans_aucun_ecart() {
        try (SqlStorage original = SqliteTestSupport.open(directory.resolve("original.db"))) {
            ContentFixture.populate(original);
            try (SqlStorage server = database.open("mig_")) {
                report("SQLite -> " + ServerDatabase.product(), migrate(original, server));
            }
            try (SqlStorage server = database.open("mig_");
                 SqlStorage back = SqliteTestSupport.open(directory.resolve("retour.db"))) {
                ContentFixture.assertSameContent(original.content(), server.content());
                assertThat(ContentInvariants.violations(server.content())).isEmpty();
                report(ServerDatabase.product() + " -> SQLite", migrate(server, back));
            }
            try (SqlStorage back = SqliteTestSupport.open(directory.resolve("retour.db"))) {
                ContentFixture.assertSameContent(original.content(), back.content());
            }
        }
    }

    private static MigrationReport migrate(SqlStorage source, SqlStorage target) {
        return new StorageMigration(line -> { }, Clock.systemUTC()).migrate(source.content(), target.content());
    }

    private static void report(String direction, MigrationReport report) {
        System.out.println("MIGRATION " + direction + " : " + report.describe());
    }
}
