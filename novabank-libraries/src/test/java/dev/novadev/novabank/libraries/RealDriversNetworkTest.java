package dev.novadev.novabank.libraries;

import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.config.model.StorageType;
import dev.novadev.novabank.libraries.artifact.ArtifactChecksums;
import dev.novadev.novabank.libraries.artifact.LibraryArtifact;
import dev.novadev.novabank.libraries.cache.LocalLibraryCache;
import dev.novadev.novabank.libraries.catalog.DriverCatalog;
import dev.novadev.novabank.libraries.catalog.ModuleCatalog;
import dev.novadev.novabank.libraries.classloading.IsolatedClassLoader;
import dev.novadev.novabank.libraries.download.ArtifactDownloader;
import dev.novadev.novabank.libraries.download.DownloadSettings;
import dev.novadev.novabank.libraries.module.EmbeddedModules;
import dev.novadev.novabank.libraries.testing.RecordingLogger;
import dev.novadev.novabank.storage.sql.SqlStorage;
import dev.novadev.novabank.storage.sql.dialect.SqliteDialect;
import dev.novadev.novabank.storage.sql.pool.ConnectionPools;
import dev.novadev.novabank.storage.sql.pool.DriverDataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration reelle : telechargement des vrais pilotes depuis Maven Central.
 * Desactivable avec {@code -DexcludedGroups=network}.
 */
@Tag("network")
@Timeout(300)
class RealDriversNetworkTest {

    @TempDir
    Path root;

    private final RecordingLogger logger = new RecordingLogger();

    @Test
    void sqlite_telecharge_verifie_isole_et_branche_sur_le_stockage_sql() throws Exception {
        Path libs = root.resolve("plugins/NovaBank/libs");
        JdbcDrivers drivers = JdbcDrivers.create(libs, List.of(), getClass().getClassLoader(), logger);
        String previousNativeDir = System.clearProperty("org.sqlite.tmpdir");
        try (LoadedDriver loaded = drivers.forStorage(StorageType.SQLITE).orElseThrow()) {
            Driver driver = loaded.driver();
            LibraryArtifact artifact = DriverCatalog.SQLITE.artifacts().get(0);

            assertThat(ArtifactChecksums.isIntact(artifact, libs.resolve(artifact.fileName()))).isTrue();
            assertThat(driver.getClass().getClassLoader()).isInstanceOf(IsolatedClassLoader.class);
            assertThat(driver.getClass())
                    .as("version epinglee prioritaire sur celle du chargeur parent")
                    .isNotSameAs(Class.forName("org.sqlite.JDBC"));

            SqliteDialect dialect = new SqliteDialect();
            Path database = root.resolve("bank.db");
            try (SqlStorage storage = SqlStorage.open(ConnectionPools.sqlite(database, driver, dialect, 2),
                    dialect, SqlStorage.DEFAULT_TABLE_PREFIX, Clock.systemUTC())) {
                assertThat(storage.schemaVersion()).isEqualTo(3);
            }
            DriverDataSource direct = new DriverDataSource(driver, dialect.jdbcUrl(database),
                    dialect.connectionProperties(), dialect.connectionInitStatements());
            try (Connection connection = direct.getConnection();
                 Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery(
                         "SELECT sqlite_version(), COUNT(*) FROM sqlite_master WHERE name LIKE 'novabank_%'")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).startsWith("3.");
                assertThat(result.getInt(2)).isGreaterThanOrEqualTo(5);
            }
            Path nativeDir = libs.toAbsolutePath().resolve(JdbcDrivers.NATIVE_DIRECTORY);
            try (Stream<Path> files = Files.list(nativeDir)) {
                assertThat(files.map(file -> file.getFileName().toString()))
                        .as("bibliotheque native extraite dans libs/native")
                        .anyMatch(name -> name.startsWith("sqlite-"));
            }
        } finally {
            restore(previousNativeDir);
        }
        assertThat(logger.lines()).anyMatch(line -> line.contains("Telechargement de org.xerial:sqlite-jdbc"));

        RecordingLogger secondStart = new RecordingLogger();
        try (LoadedDriver again = JdbcDrivers.create(libs, List.of(), getClass().getClassLoader(), secondStart)
                .forStorage(StorageType.SQLITE).orElseThrow()) {
            assertThat(again.driver().acceptsURL("jdbc:sqlite:x.db")).isTrue();
        } finally {
            restore(previousNativeDir);
        }
        assertThat(secondStart.lines()).as("second demarrage depuis le cache").noneMatch(line -> line.contains("Telechargement"));
    }

    @Test
    void mariadb_telecharge_verifie_et_instancie_pour_mysql_et_mariadb() throws Exception {
        Path libs = root.resolve("libs");
        JdbcDrivers drivers = JdbcDrivers.create(libs, List.of(), getClass().getClassLoader(), logger);

        try (LoadedDriver loaded = drivers.forStorage(StorageType.MYSQL).orElseThrow()) {
            Driver driver = loaded.driver();
            LibraryArtifact artifact = DriverCatalog.MARIADB.artifacts().get(0);

            assertThat(ArtifactChecksums.isIntact(artifact, libs.resolve(artifact.fileName()))).isTrue();
            assertThat(driver.getClass().getName()).isEqualTo("org.mariadb.jdbc.Driver");
            assertThat(driver.getClass().getClassLoader()).isInstanceOf(IsolatedClassLoader.class);
            assertThat(driver.acceptsURL("jdbc:mariadb://127.0.0.1:3306/novabank")).isTrue();
            assertThat(driver.getMajorVersion()).isEqualTo(3);
        }
        try (LoadedDriver loaded = drivers.forStorage(StorageType.MARIADB).orElseThrow()) {
            assertThat(loaded.spec()).isSameAs(DriverCatalog.MARIADB);
        }
        assertThat(logger.lines()).filteredOn(line -> line.contains("Telechargement de")).hasSize(1);
    }

    @Test
    void les_pilotes_mongodb_epingles_sont_telecharges_et_verifies() throws Exception {
        Path libs = root.resolve("libs");
        LocalLibraryCache cache = new LocalLibraryCache(libs,
                new ArtifactDownloader(DownloadSettings.defaults(List.of()), logger), logger);

        List<Path> jars = cache.resolveAll(ModuleCatalog.MONGODB.libraries());

        for (LibraryArtifact artifact : ModuleCatalog.MONGODB.libraries()) {
            assertThat(ArtifactChecksums.isIntact(artifact, libs.resolve(artifact.fileName()))).as(artifact.coordinates())
                    .isTrue();
        }
        try (IsolatedClassLoader loader = new IsolatedClassLoader("mongo-test", IsolatedClassLoader.toUrls(jars),
                getClass().getClassLoader(), EmbeddedModules.PARENT_FIRST_PREFIXES)) {
            assertThat(loader.loadClass("com.mongodb.client.MongoClients").getClassLoader()).isSameAs(loader);
            assertThat(loader.loadClass("org.bson.codecs.record.RecordCodecProvider").getClassLoader()).isSameAs(loader);
        }
    }

    private static void restore(String previous) {
        if (previous == null) {
            System.clearProperty("org.sqlite.tmpdir");
        } else {
            System.setProperty("org.sqlite.tmpdir", previous);
        }
    }
}
