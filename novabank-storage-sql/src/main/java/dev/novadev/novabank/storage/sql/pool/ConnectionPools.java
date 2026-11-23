package dev.novadev.novabank.storage.sql.pool;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.novadev.novabank.core.repository.StorageException;
import dev.novadev.novabank.core.text.ConsoleText;
import dev.novadev.novabank.storage.sql.dialect.MariaDbDialect;
import dev.novadev.novabank.storage.sql.dialect.SqliteDialect;
import java.nio.file.Path;
import java.sql.Driver;
import java.util.Objects;
import java.util.Properties;

/**
 * Fabrique des pools de connexions HikariCP.
 */
public final class ConnectionPools {

    /**
     * Taille par defaut du pool SQLite : quelques lecteurs simultanes (WAL), les ecritures
     * etant de toute facon serialisees et ne tenant qu'une connexion a la fois.
     */
    public static final int DEFAULT_SQLITE_POOL_SIZE = 4;

    private static final long CONNECTION_TIMEOUT_MS = 10_000L;

    private ConnectionPools() {
    }

    /**
     * Ouvre un pool sur un fichier SQLite (cree s'il n'existe pas ; son dossier doit exister).
     *
     * @param file        fichier de base
     * @param driver      pilote SQLite, fourni par la plateforme
     * @param dialect     dialecte SQLite (reglages de session)
     * @param maxPoolSize nombre maximal de connexions, au moins 1
     * @return le pool ouvert, avec au moins une connexion valide
     * @throws StorageException si la base ne peut pas etre ouverte
     */
    public static HikariDataSource sqlite(Path file, Driver driver, SqliteDialect dialect, int maxPoolSize) {
        Objects.requireNonNull(file, "file");
        if (maxPoolSize < 1) {
            throw new IllegalArgumentException(ConsoleText.tr("Taille de pool invalide : {0}", maxPoolSize));
        }
        HikariConfig config = new HikariConfig();
        config.setPoolName("NovaBank-SQLite");
        config.setDataSource(new DriverDataSource(driver, dialect.jdbcUrl(file), dialect.connectionProperties(),
                dialect.connectionInitStatements()));
        config.setMaximumPoolSize(maxPoolSize);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(CONNECTION_TIMEOUT_MS);
        try {
            return new HikariDataSource(config);
        } catch (RuntimeException failure) {
            throw new StorageException(ConsoleText.tr("Impossible d'ouvrir la base SQLite {0} : {1}",
                    file.toAbsolutePath(), rootMessage(failure)), failure);
        }
    }

    /**
     * Ouvre un pool sur un serveur MySQL / MariaDB et verifie qu'une connexion s'etablit.
     *
     * <p>Proprietes du pilote, par priorite croissante : delai de connexion
     * ({@code connectTimeout}, egal a l'attente maximale du pool), options de la configuration,
     * proprietes imposees par le dialecte, puis identifiants. Les identifiants ne sont jamais
     * dans l'URL et le message d'erreur est expurge du mot de passe.
     *
     * @param poolName   nom du pool (threads HikariCP)
     * @param connection serveur, identifiants et options
     * @param driver     pilote MariaDB, fourni par la plateforme
     * @param dialect    dialecte (URL, proprietes et reglages de session)
     * @param pool       dimensionnement du pool
     * @return le pool ouvert, avec au moins une connexion valide
     * @throws StorageException         si le serveur est injoignable ou refuse la connexion
     * @throws IllegalArgumentException si l'hote, le port ou la base sont invalides
     */
    public static HikariDataSource server(String poolName, ServerConnection connection, Driver driver,
                                          MariaDbDialect dialect, PoolOptions pool) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(pool, "pool");
        String url = dialect.jdbcUrl(connection.host(), connection.port(), connection.database());
        Properties properties = new Properties();
        properties.setProperty("connectTimeout", Long.toString(pool.connectionTimeoutMs()));
        properties.putAll(connection.options());
        properties.putAll(dialect.connectionProperties());
        properties.setProperty("user", connection.user());
        properties.setProperty("password", connection.password());
        HikariConfig config = new HikariConfig();
        config.setPoolName(poolName);
        config.setDataSource(new DriverDataSource(driver, url, properties, dialect.connectionInitStatements()));
        config.setMaximumPoolSize(pool.maxSize());
        config.setMinimumIdle(pool.minIdle());
        config.setConnectionTimeout(pool.connectionTimeoutMs());
        config.setMaxLifetime(pool.maxLifetimeMs());
        try {
            return new HikariDataSource(config);
        } catch (RuntimeException failure) {
            // Sans cause : la pile du pilote pourrait reprendre des proprietes de connexion.
            String reason = rootMessage(failure);
            throw new StorageException(ConsoleText.tr("Connexion impossible au serveur SQL {0} : {1}{2}",
                    connection.describe(), connection.mask(reason), dialect.connectionFailureHint(reason).map(hint -> ". " + hint).orElse("")));
        }
    }

    private static String rootMessage(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return String.valueOf(root.getMessage());
    }
}
