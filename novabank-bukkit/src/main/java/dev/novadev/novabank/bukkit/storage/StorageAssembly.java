package dev.novadev.novabank.bukkit.storage;

import dev.novadev.novabank.bukkit.lifecycle.Lifecycle;
import dev.novadev.novabank.bukkit.lifecycle.StartupException;
import dev.novadev.novabank.bukkit.message.LogMessages;
import dev.novadev.novabank.config.model.MongoSettings;
import dev.novadev.novabank.config.model.PoolSettings;
import dev.novadev.novabank.config.model.SqlSettings;
import dev.novadev.novabank.config.model.StorageSettings;
import dev.novadev.novabank.config.model.StorageType;
import dev.novadev.novabank.core.repository.StorageException;
import dev.novadev.novabank.core.storage.BackendParameters;
import dev.novadev.novabank.core.storage.StorageBackend;
import dev.novadev.novabank.core.storage.StorageBackendFactory;
import dev.novadev.novabank.core.text.ConsoleText;
import dev.novadev.novabank.libraries.JdbcDrivers;
import dev.novadev.novabank.libraries.LoadedDriver;
import dev.novadev.novabank.libraries.catalog.DriverCatalog;
import dev.novadev.novabank.libraries.catalog.DriverSpec;
import dev.novadev.novabank.libraries.catalog.ModuleCatalog;
import dev.novadev.novabank.libraries.module.EmbeddedModule;
import dev.novadev.novabank.libraries.module.EmbeddedModules;
import dev.novadev.novabank.libraries.module.LoadedModule;
import dev.novadev.novabank.storage.file.FileFormat;
import dev.novadev.novabank.storage.file.FileStorage;
import dev.novadev.novabank.storage.file.FileStorageOptions;
import dev.novadev.novabank.storage.sql.SqlStorage;
import dev.novadev.novabank.storage.sql.dialect.MariaDbDialect;
import dev.novadev.novabank.storage.sql.dialect.SqliteDialect;
import dev.novadev.novabank.storage.sql.pool.ConnectionPools;
import dev.novadev.novabank.storage.sql.pool.PoolOptions;
import dev.novadev.novabank.storage.sql.pool.ServerConnection;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Driver;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.logging.Logger;
import javax.sql.DataSource;

/**
 * Ouvre le backend configure et enregistre ses ressources dans le cycle de vie (pilote ou module
 * puis stockage : fermes dans l'ordre inverse).
 *
 * <p>Backends disponibles : SQLite (fichier local), MySQL / MariaDB (serveur partage, pilote
 * MariaDB telecharge et isole, D6) et MongoDB (module embarque charge en chargeur isole avec le
 * pilote telecharge, D18), YAML et JSON (fichiers locaux, mono-serveur D4, formats lus par le Gson et
 * la SnakeYAML fournis par le serveur).
 */
public final class StorageAssembly {

    private final Path dataFolder;
    private final Clock clock;
    private final Logger logger;

    /**
     * @param dataFolder dossier du plugin, base des chemins relatifs
     * @param clock      horloge du stockage
     * @param logger     journal du plugin, transmis aux modules embarques
     */
    public StorageAssembly(Path dataFolder, Clock clock, Logger logger) {
        this.dataFolder = Objects.requireNonNull(dataFolder, "dataFolder");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Dossier des pilotes telecharges et des modules extraits.
     *
     * @param settings section stockage
     * @return le dossier, relatif au dossier du plugin
     */
    public Path driverDirectory(StorageSettings settings) {
        return dataFolder.resolve(settings.drivers().directory()).normalize();
    }

    /**
     * @param settings  section stockage
     * @param drivers   chargeur de pilotes JDBC
     * @param modules   chargeur de modules embarques
     * @param lifecycle cycle de vie qui recoit pilote (ou module) et stockage
     * @return le stockage ouvert
     * @throws StartupException si le backend n'est pas disponible, le dossier de la base non
     *                          creable ou le serveur injoignable (message sans secret)
     */
    public ActiveStorage open(StorageSettings settings, JdbcDrivers drivers, EmbeddedModules modules,
                              Lifecycle lifecycle) {
        if (settings.type() == StorageType.YAML || settings.type() == StorageType.JSON) {
            return openFile(settings, lifecycle);
        }
        if (settings.type() == StorageType.MONGODB) {
            return openMongo(settings.mongodb(), modules, lifecycle);
        }
        DriverSpec spec = DriverCatalog.forStorage(settings.type()).orElseThrow();
        if (settings.type() == StorageType.SQLITE) {
            // Dossier verifie avant tout telechargement de pilote.
            Path database = dataFolder.resolve(settings.sqlite().file()).normalize();
            createParent(database);
            LoadedDriver driver = lifecycle.register("pilote " + spec.id(), drivers.load(spec));
            SqliteDialect dialect = new SqliteDialect();
            SqlStorage storage = lifecycle.register("stockage SQLite",
                    SqlStorage.open(ConnectionPools.sqlite(database, driver.driver(), dialect,
                            ConnectionPools.DEFAULT_SQLITE_POOL_SIZE), dialect, SqlStorage.DEFAULT_TABLE_PREFIX, clock));
            return new ActiveStorage(settings.type(), storage, describe(driver), database.toString());
        }
        // Configuration verifiee avant tout telechargement de pilote.
        ServerConnection connection = serverConnection(settings.sql());
        LoadedDriver driver = lifecycle.register("pilote " + spec.id(), drivers.load(spec));
        SqlStorage storage = lifecycle.register("stockage " + settings.type(),
                openServer(settings.type(), connection, toPoolOptions(settings.sql().pool()), driver.driver(), clock));
        return new ActiveStorage(settings.type(), storage, describe(driver), connection.describe());
    }

    /**
     * Ouvre un stockage fichier YAML ou JSON dans le dossier configure (relatif au dossier du
     * plugin). L'intervalle de sauvegarde borne l'anciennete du journal d'ecriture avant
     * compaction.
     *
     * @param settings  section stockage (type YAML ou JSON)
     * @param lifecycle cycle de vie qui recoit le stockage
     * @return le stockage ouvert
     * @throws StartupException si la bibliotheque du format manque, si les donnees sont deja
     *                          utilisees par un autre serveur, illisibles ou corrompues
     */
    ActiveStorage openFile(StorageSettings settings, Lifecycle lifecycle) {
        FileFormat format = settings.type() == StorageType.JSON ? FileFormat.JSON : FileFormat.YAML;
        String library = format == FileFormat.JSON ? "com.google.gson.stream.JsonWriter" : "org.yaml.snakeyaml.Yaml";
        String libraryPom = format == FileFormat.JSON ? "META-INF/maven/com.google.code.gson/gson/pom.properties"
                : "META-INF/maven/org.yaml/snakeyaml/pom.properties";
        Class<?> libraryClass;
        try {
            libraryClass = Class.forName(library, false, StorageAssembly.class.getClassLoader());
        } catch (ClassNotFoundException absent) {
            throw new StartupException(LogMessages.fileFormatLibraryMissing(settings.type(), library));
        }
        Path directory = dataFolder.resolve(settings.file().directory()).normalize();
        FileStorage storage;
        try {
            storage = FileStorage.open(FileStorageOptions.of(directory, format,
                    Duration.ofSeconds(settings.saveIntervalSeconds()), clock, logger));
        } catch (StorageException failure) {
            throw new StartupException(LogMessages.fileStorageUnavailable(settings.type(), failure.getMessage()));
        }
        lifecycle.register("stockage " + settings.type(), storage);
        String detail = (format == FileFormat.JSON ? "Gson " : "SnakeYAML ") + libraryVersion(libraryClass, libraryPom)
                + " fourni par le serveur";
        logger.info(ConsoleText.tr("Stockage {0} : {1}", settings.type(), detail));
        return new ActiveStorage(settings.type(), storage, new StorageDriver(settings.type().name().toLowerCase(Locale.ROOT),
                "format " + FileStorage.FORMAT_VERSION, detail), storage.snapshotFile().toString());
    }

    /**
     * @return la version de la bibliotheque du serveur (descripteur Maven de son jar), ou
     *         "version inconnue"
     */
    private static String libraryVersion(Class<?> libraryClass, String pomProperties) {
        ClassLoader loader = libraryClass.getClassLoader();
        try (InputStream input = loader == null ? null : loader.getResourceAsStream(pomProperties)) {
            if (input != null) {
                Properties pom = new Properties();
                pom.load(input);
                String version = pom.getProperty("version");
                if (version != null) {
                    return version;
                }
            }
        } catch (IOException unreadable) {
            // Version seulement informative.
        }
        Package library = libraryClass.getPackage();
        String version = library == null ? null : library.getImplementationVersion();
        return version == null ? "version inconnue" : version;
    }

    /**
     * Ouvre MongoDB : module embarque (extrait, verifie, charge avec ses pilotes epingles), puis
     * backend ouvert par la fabrique du module, avec le chargeur du module comme chargeur de
     * contexte (les threads du pilote en heritent).
     */
    private ActiveStorage openMongo(MongoSettings settings, EmbeddedModules modules, Lifecycle lifecycle) {
        BackendParameters parameters = mongoParameters(settings);
        String location = MongoLocation.describe(settings.uri().reveal(), settings.database());
        EmbeddedModule spec = ModuleCatalog.MONGODB;
        LoadedModule<StorageBackendFactory> module = lifecycle.register("module " + spec.id(),
                modules.load(spec, StorageBackendFactory.class));
        StorageBackend backend;
        try {
            backend = module.withContextClassLoader(() -> module.entry().open(parameters));
        } catch (StorageException failure) {
            // Message deja expurge par le module (URI et mot de passe masques).
            throw new StartupException(LogMessages.mongoUnavailable(location, failure.getMessage()));
        } catch (IllegalArgumentException invalid) {
            throw new StartupException(ConsoleText.tr("storage.mongodb : {0}", invalid.getMessage()));
        }
        lifecycle.register("stockage " + StorageType.MONGODB, backend);
        return new ActiveStorage(StorageType.MONGODB, backend, new StorageDriver(spec.id(),
                ModuleCatalog.MONGODB_DRIVER_VERSION, "module " + spec.resource()), location);
    }

    /**
     * Cles lues par le module MongoDB ({@code MongoConnectionSettings}, non visible d'ici : le
     * module n'est connu que par son nom de classe).
     */
    BackendParameters mongoParameters(MongoSettings settings) {
        return BackendParameters.builder(clock, logger)
                .putSecret("uri", settings.uri().reveal())
                .put("database", settings.database())
                .put("connect-timeout-ms", settings.connectTimeoutMs())
                .put("server-selection-timeout-ms", settings.serverSelectionTimeoutMs())
                .put("socket-timeout-ms", settings.socketTimeoutMs())
                .build();
    }

    private static StorageDriver describe(LoadedDriver driver) {
        Driver jdbc = driver.driver();
        return new StorageDriver(driver.spec().id(), driver.spec().artifacts().get(0).version(),
                "JDBC " + jdbc.getMajorVersion() + "." + jdbc.getMinorVersion());
    }

    /**
     * Ouvre un stockage MySQL / MariaDB : pool, puis migration du schema.
     *
     * @param type       MYSQL ou MARIADB (pour les messages)
     * @param connection serveur et identifiants
     * @param poolOptions dimensionnement du pool
     * @param driver     pilote MariaDB
     * @param clock      horloge du stockage
     * @return le stockage ouvert
     * @throws StartupException si le serveur est injoignable, refuse la connexion, ou si le schema
     *                          ne peut pas etre migre ; le message ne contient jamais le mot de passe
     */
    static SqlStorage openServer(StorageType type, ServerConnection connection, PoolOptions poolOptions,
                                 Driver driver, Clock clock) {
        MariaDbDialect dialect = new MariaDbDialect();
        DataSource pool;
        try {
            pool = ConnectionPools.server("NovaBank-" + type, connection, driver, dialect, poolOptions);
        } catch (StorageException | IllegalArgumentException failure) {
            // Le message du pool nomme deja hote, port et base.
            throw new StartupException(LogMessages.sqlServerUnavailable(type, connection.mask(failure.getMessage())));
        }
        try {
            return SqlStorage.open(pool, dialect, SqlStorage.DEFAULT_TABLE_PREFIX, clock);
        } catch (StorageException failure) {
            throw new StartupException(LogMessages.sqlServerUnavailable(type,
                    ConsoleText.tr("{0} : {1}", connection.describe(), connection.mask(failure.getMessage()))));
        }
    }

    private static ServerConnection serverConnection(SqlSettings sql) {
        try {
            return new ServerConnection(sql.host(), sql.port(), sql.database(), sql.username().reveal(),
                    sql.password().reveal(), sql.options());
        } catch (IllegalArgumentException invalid) {
            throw new StartupException(ConsoleText.tr("storage.sql : {0}", invalid.getMessage()));
        }
    }

    private static PoolOptions toPoolOptions(PoolSettings pool) {
        return new PoolOptions(pool.maxSize(), pool.minIdle(), pool.connectionTimeoutMs(), pool.maxLifetimeMs());
    }

    private static void createParent(Path database) {
        Path parent = database.getParent();
        if (parent == null) {
            return;
        }
        try {
            Files.createDirectories(parent);
        } catch (IOException failure) {
            throw new StartupException(LogMessages.databaseFolderUnavailable(parent, failure));
        }
    }
}
