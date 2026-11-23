package dev.novadev.novabank.libraries;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.novadev.novabank.config.model.StorageType;
import dev.novadev.novabank.libraries.artifact.LibraryArtifact;
import dev.novadev.novabank.libraries.cache.LocalLibraryCache;
import dev.novadev.novabank.libraries.catalog.DriverSpec;
import dev.novadev.novabank.libraries.classloading.IsolatedClassLoader;
import dev.novadev.novabank.libraries.download.ArtifactDownloader;
import dev.novadev.novabank.libraries.download.DownloadSettings;
import dev.novadev.novabank.libraries.testing.FakeRepositoryServer;
import dev.novadev.novabank.libraries.testing.RecordingLogger;
import dev.novadev.novabank.libraries.testing.TestArtifacts;
import dev.novadev.novabank.libraries.testing.TestJars;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Driver;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Fabrique de pilotes, sans reseau : un faux pilote JDBC compile a la volee est place dans
 * le cache, le faux depot doit rester sans requete.
 */
class JdbcDriversTest {

    private static final String FAKE_DRIVER = "org.example.fakedriver.FakeDriver";
    private static final String NOT_A_DRIVER = "org.example.fakedriver.NotADriver";

    private static final String FAKE_DRIVER_SOURCE = """
            package org.example.fakedriver;

            import java.sql.*;
            import java.util.Properties;
            import java.util.logging.Logger;

            public class FakeDriver implements Driver {
                static {
                    try {
                        DriverManager.registerDriver(new FakeDriver());
                    } catch (SQLException failure) {
                        throw new ExceptionInInitializerError(failure);
                    }
                }
                public Connection connect(String url, Properties info) { return null; }
                public boolean acceptsURL(String url) { return url.startsWith("jdbc:fake:"); }
                public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) { return new DriverPropertyInfo[0]; }
                public int getMajorVersion() { return 4; }
                public int getMinorVersion() { return 2; }
                public boolean jdbcCompliant() { return false; }
                public Logger getParentLogger() throws SQLFeatureNotSupportedException {
                    throw new SQLFeatureNotSupportedException();
                }
            }
            """;

    @TempDir
    Path root;

    private final FakeRepositoryServer server = new FakeRepositoryServer();
    private final RecordingLogger logger = new RecordingLogger();
    private final String nativeProperty = "novabank.test.native." + UUID.randomUUID();
    private Path libs;
    private LibraryArtifact jarArtifact;

    @BeforeEach
    void placeFakeDriverInCache() throws IOException {
        Path jar = TestJars.build(root, "fake-driver.jar", Map.of(
                FAKE_DRIVER, FAKE_DRIVER_SOURCE,
                NOT_A_DRIVER, "package org.example.fakedriver; public class NotADriver { }"), Map.of());
        jarArtifact = TestArtifacts.pinned("fake-driver", jar);
        libs = Files.createDirectories(root.resolve("libs"));
        Files.copy(jar, libs.resolve(jarArtifact.fileName()));
    }

    @AfterEach
    void cleanUp() {
        System.clearProperty(nativeProperty);
        server.close();
    }

    private JdbcDrivers drivers() {
        ArtifactDownloader downloader = new ArtifactDownloader(new DownloadSettings(List.of(server.baseUri()),
                Duration.ofSeconds(1), Duration.ofSeconds(1), 1, Duration.ZERO), logger);
        return new JdbcDrivers(new LocalLibraryCache(libs, downloader, logger), getClass().getClassLoader(), logger);
    }

    private DriverSpec spec(String className, Optional<String> nativeDirectoryProperty) {
        return new DriverSpec("fake", className, List.of(jarArtifact), nativeDirectoryProperty);
    }

    @Test
    void les_prefixes_parent_first_couvrent_le_jdk_le_slf4j_du_serveur_et_novabank() {
        assertThat(JdbcDrivers.PARENT_FIRST_PREFIXES)
                .containsExactly("java.", "javax.", "jdk.", "sun.", "org.slf4j.", "dev.novadev.novabank.");
    }

    @Test
    void charge_le_pilote_dans_un_chargeur_isole_sans_reseau() throws Exception {
        try (LoadedDriver loaded = drivers().load(spec(FAKE_DRIVER, Optional.empty()))) {
            Driver driver = loaded.driver();

            assertThat(driver.getClass().getName()).isEqualTo(FAKE_DRIVER);
            assertThat(driver.getClass().getClassLoader()).isInstanceOf(IsolatedClassLoader.class);
            assertThat(driver.acceptsURL("jdbc:fake:db")).isTrue();
            assertThat(loaded.spec().id()).isEqualTo("fake");
            assertThat(Collections.list(DriverManager.getDrivers()))
                    .as("pilote invisible depuis le chargeur du plugin")
                    .noneMatch(registered -> registered.getClass().getName().equals(FAKE_DRIVER));
        }
        assertThat(server.totalRequests()).isZero();
        assertThat(logger.lines()).anyMatch(line -> line.contains("Pilote fake charge") && line.contains("4.2"));
    }

    @Test
    void la_fermeture_desinscrit_le_pilote_et_reste_idempotente() {
        LoadedDriver loaded = drivers().load(spec(FAKE_DRIVER, Optional.empty()));

        loaded.close();
        loaded.close();

        assertThat(logger.lines()).filteredOn(line -> line.contains("decharge"))
                .singleElement().satisfies(line -> assertThat(line).contains("(1 inscription"));
    }

    @Test
    void dossier_natif_configure_si_la_propriete_est_libre() {
        try (LoadedDriver loaded = drivers().load(spec(FAKE_DRIVER, Optional.of(nativeProperty)))) {
            Path nativeDir = libs.toAbsolutePath().normalize().resolve(JdbcDrivers.NATIVE_DIRECTORY);
            assertThat(System.getProperty(nativeProperty)).isEqualTo(nativeDir.toString());
            assertThat(nativeDir).isDirectory();
            assertThat(loaded.driver()).isNotNull();
        }
    }

    @Test
    void propriete_native_deja_definie_conservee() {
        System.setProperty(nativeProperty, "/opt/custom");

        try (LoadedDriver loaded = drivers().load(spec(FAKE_DRIVER, Optional.of(nativeProperty)))) {
            assertThat(loaded.driver()).isNotNull();
            assertThat(System.getProperty(nativeProperty)).isEqualTo("/opt/custom");
            assertThat(logger.lines()).anyMatch(line -> line.contains(nativeProperty) && line.contains("conservee"));
        }
    }

    @Test
    void dossier_natif_impossible_a_creer() throws IOException {
        Files.write(libs.resolve(JdbcDrivers.NATIVE_DIRECTORY), new byte[1]);

        assertThatThrownBy(() -> drivers().load(spec(FAKE_DRIVER, Optional.of(nativeProperty))))
                .isInstanceOf(LibraryException.class)
                .hasMessageContaining("dossier natif");
    }

    @Test
    void classe_qui_n_est_pas_un_pilote_refusee() {
        assertThatThrownBy(() -> drivers().load(spec(NOT_A_DRIVER, Optional.empty())))
                .isInstanceOf(LibraryException.class)
                .hasMessageContaining("n'est pas un pilote JDBC");
    }

    @Test
    void classe_absente_erreur_claire() {
        assertThatThrownBy(() -> drivers().load(spec("org.example.fakedriver.Missing", Optional.empty())))
                .isInstanceOf(LibraryException.class)
                .hasMessageContaining("Instanciation du pilote fake")
                .hasCauseInstanceOf(ClassNotFoundException.class);
    }

    @Test
    void artefact_introuvable_erreur_claire() throws IOException {
        Files.move(libs.resolve(jarArtifact.fileName()), root.resolve("moved.jar"));

        assertThatThrownBy(() -> drivers().load(spec(FAKE_DRIVER, Optional.empty())))
                .isInstanceOf(LibraryException.class)
                .hasMessageContaining("HTTP 404");
    }

    @Test
    void backend_sans_jdbc_aucun_pilote() {
        assertThat(drivers().forStorage(StorageType.YAML)).isEmpty();
        assertThat(drivers().forStorage(StorageType.MONGODB)).isEmpty();
        assertThat(server.totalRequests()).isZero();
    }

    @Test
    void fabrique_par_defaut() {
        JdbcDrivers drivers = JdbcDrivers.create(libs, List.of(), getClass().getClassLoader(), logger);

        assertThat(drivers.forStorage(StorageType.JSON)).isEmpty();
    }
}
