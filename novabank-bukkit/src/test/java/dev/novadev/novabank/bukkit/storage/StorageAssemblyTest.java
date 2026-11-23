package dev.novadev.novabank.bukkit.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.novadev.novabank.bukkit.config.BukkitItemAvailability;
import dev.novadev.novabank.bukkit.config.YamlTrees;
import dev.novadev.novabank.bukkit.lifecycle.Lifecycle;
import dev.novadev.novabank.bukkit.lifecycle.StartupException;
import dev.novadev.novabank.bukkit.testing.TestConfigs;
import dev.novadev.novabank.config.ConfigLoadResult;
import dev.novadev.novabank.config.NovaBankConfigLoader;
import dev.novadev.novabank.config.model.MongoSettings;
import dev.novadev.novabank.config.model.Secret;
import dev.novadev.novabank.config.model.StorageSettings;
import dev.novadev.novabank.config.model.StorageType;
import dev.novadev.novabank.core.storage.BackendParameters;
import dev.novadev.novabank.libraries.catalog.DriverCatalog;
import dev.novadev.novabank.libraries.catalog.ModuleCatalog;
import dev.novadev.novabank.storage.sql.pool.PoolOptions;
import dev.novadev.novabank.storage.sql.pool.ServerConnection;
import java.io.InputStream;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Clock;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class StorageAssemblyTest {

    private static final String SECRET_PASSWORD = "MotDePasse-Tres-Secret";

    @TempDir
    Path pluginFolder;

    @ParameterizedTest
    @EnumSource(value = StorageType.class, names = {"YAML", "JSON"})
    void yaml_et_json_ouvrent_un_stockage_fichier_dans_le_dossier_configure(StorageType type) throws Exception {
        StorageAssembly assembly = new StorageAssembly(pluginFolder, Clock.systemUTC(), Logger.getAnonymousLogger());
        try (Lifecycle lifecycle = new Lifecycle(Logger.getAnonymousLogger())) {
            ActiveStorage storage = assembly.openFile(settings(type), lifecycle);

            assertThat(storage.type()).isEqualTo(type);
            assertThat(storage.backend().content().isEmpty()).isTrue();
            assertThat(storage.location()).startsWith(pluginFolder.resolve("data").toString());
            assertThat(storage.driver().detail()).contains("fourni par le serveur").doesNotContain("inconnue");
            assertThatThrownBy(() -> assembly.openFile(settings(type), new Lifecycle(Logger.getAnonymousLogger())))
                    .isInstanceOf(StartupException.class)
                    .hasMessageContaining("deja utilisees par un autre serveur");
        }
    }

    @Test
    void sqlite_est_accepte_et_les_pilotes_vont_dans_libs() throws Exception {
        StorageSettings settings = settings(StorageType.SQLITE);
        StorageAssembly assembly = new StorageAssembly(Path.of("plugins", "NovaBank"), Clock.systemUTC(),
                Logger.getAnonymousLogger());

        assertThat(DriverCatalog.forStorage(StorageType.SQLITE)).contains(DriverCatalog.SQLITE);
        assertThat(assembly.driverDirectory(settings)).isEqualTo(Path.of("plugins", "NovaBank", "libs"));
    }

    @ParameterizedTest
    @EnumSource(value = StorageType.class, names = {"MYSQL", "MARIADB"})
    void mysql_et_mariadb_utilisent_le_pilote_mariadb(StorageType type) throws Exception {
        assertThat(DriverCatalog.forStorage(type)).contains(DriverCatalog.MARIADB);
    }

    @Test
    void mongodb_est_un_module_embarque_dont_les_parametres_masquent_l_uri() throws Exception {
        StorageAssembly assembly = new StorageAssembly(Path.of("plugins", "NovaBank"), Clock.systemUTC(),
                Logger.getAnonymousLogger());

        BackendParameters parameters = assembly.mongoParameters(new MongoSettings(
                Secret.of("mongodb://u:" + SECRET_PASSWORD + "@h1:27017,h2/?replicaSet=rs0"), "novabank", 5_000,
                10_000, 30_000));

        assertThat(ModuleCatalog.forStorage(StorageType.MONGODB)).contains(ModuleCatalog.MONGODB);
        assertThat(parameters.keys()).containsExactly("uri", "database", "connect-timeout-ms",
                "server-selection-timeout-ms", "socket-timeout-ms");
        assertThat(parameters.isSecret("uri")).isTrue();
        assertThat(parameters.integer("socket-timeout-ms", 100, 300_000)).isEqualTo(30_000);
        assertThat(parameters.toString()).doesNotContain(SECRET_PASSWORD);
    }

    @Test
    void l_emplacement_mongodb_ne_garde_que_les_hotes_et_la_base() {
        assertThat(MongoLocation.describe("mongodb://u:" + SECRET_PASSWORD + "@h1:27017,h2:27018/admin?replicaSet=rs0",
                "novabank")).isEqualTo("h1:27017,h2:27018/novabank");
        assertThat(MongoLocation.describe("mongodb+srv://cluster.example.net", "nb")).isEqualTo("cluster.example.net/nb");
        assertThat(MongoLocation.describe("mongodb://localhost:27017?directConnection=true", "nb"))
                .isEqualTo("localhost:27017/nb");
    }

    @Test
    void le_pilote_mariadb_epingle_est_la_version_de_test_du_projet() throws Exception {
        Properties pom = new Properties();
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("META-INF/maven/org.mariadb.jdbc/mariadb-java-client/pom.properties")) {
            assertThat(in).as("mariadb-java-client de test sur le classpath").isNotNull();
            pom.load(in);
        }
        assertThat(DriverCatalog.MARIADB.artifacts().get(0).version()).isEqualTo(pom.getProperty("version"));
    }

    @ParameterizedTest
    @EnumSource(value = StorageType.class, names = {"MYSQL", "MARIADB"})
    void un_serveur_sql_injoignable_arrete_le_demarrage_avec_un_message_sans_mot_de_passe(StorageType type)
            throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        ServerConnection connection = new ServerConnection("127.0.0.1", closedPort, "novabank", "novabank",
                SECRET_PASSWORD, Map.of());

        assertThatThrownBy(() -> StorageAssembly.openServer(type, connection, new PoolOptions(2, 0, 1_000L, 30_000L),
                DriverManager.getDriver("jdbc:mariadb:"), Clock.systemUTC()))
                .isInstanceOf(StartupException.class)
                .hasMessageContaining("Stockage " + type + " indisponible")
                .hasMessageContaining("127.0.0.1:" + closedPort + "/novabank")
                .hasMessageContaining("NovaBank ne demarre pas")
                .hasMessageNotContaining(SECRET_PASSWORD);
    }

    private static StorageSettings settings(StorageType type) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(TestConfigs.replaceOnce(TestConfigs.defaultYaml(), "type: SQLITE", "type: " + type.name()));
        ConfigLoadResult result = new NovaBankConfigLoader(new BukkitItemAvailability(id -> true, name -> true))
                .load(YamlTrees.toMap(yaml));
        return result.config().orElseThrow(() -> new AssertionError(result.report().issues().toString())).storage();
    }
}
