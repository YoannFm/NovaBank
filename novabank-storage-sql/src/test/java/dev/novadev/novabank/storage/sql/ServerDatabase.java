package dev.novadev.novabank.storage.sql;

import com.zaxxer.hikari.HikariDataSource;
import dev.novadev.novabank.storage.sql.dialect.MariaDbDialect;
import dev.novadev.novabank.storage.sql.pool.ConnectionPools;
import dev.novadev.novabank.storage.sql.pool.DriverDataSource;
import dev.novadev.novabank.storage.sql.pool.PoolOptions;
import dev.novadev.novabank.storage.sql.pool.ServerConnection;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;

/**
 * Base de test sur un vrai serveur MySQL / MariaDB, creee vide puis supprimee a la fermeture.
 *
 * <p>Serveur designe par les proprietes systeme {@code novabank.it.db.<cle>} ou, a defaut, les
 * variables d'environnement {@code NOVABANK_IT_DB_<CLE>} exportees par
 * {@code scripts/db/with-db.sh} : {@code product} (mariadb | mysql), {@code host}, {@code port},
 * {@code user}, {@code password}, {@code name} (base existante pour la connexion
 * d'administration), {@code ctl} (script {@code db-server.sh}, pour arreter et relancer le
 * serveur dans les tests de panne). Sans {@code port}, les tests {@link RequiresSqlServer}
 * sont ignores. L'utilisateur doit pouvoir creer et supprimer les bases {@code novabank_it_*}.
 *
 * <p>Le pilote MariaDB vient du classpath de test et sert pour les deux serveurs, comme en
 * production (D6) ; aucune classe {@code org.mariadb} n'est nommee.
 */
final class ServerDatabase implements AutoCloseable {

    /** Tag JUnit des tests sur serveur. */
    static final String TAG = "sql-server";

    /** Dialecte par defaut des tests. */
    static final MariaDbDialect DIALECT = new MariaDbDialect();

    /** Pool de test : petit, attente courte (les pannes doivent echouer vite). */
    static final PoolOptions POOL = new PoolOptions(4, 0, 3_000L, 60_000L);

    private final String name;

    private ServerDatabase(String name) {
        this.name = name;
    }

    /**
     * @return {@code true} si un serveur est configure
     */
    static boolean available() {
        return setting("port") != null;
    }

    /**
     * @return {@code true} si le script de pilotage du serveur est fourni (tests de panne)
     */
    static boolean controllable() {
        return available() && setting("ctl") != null;
    }

    /**
     * @return le produit teste ({@code mariadb} ou {@code mysql})
     */
    static String product() {
        return String.valueOf(setting("product")).toLowerCase(Locale.ROOT);
    }

    /**
     * Cree une base vide au nom aleatoire.
     */
    static ServerDatabase create() throws SQLException {
        String name = "novabank_it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        try (Connection admin = unpooled(setting("name"), DIALECT).getConnection();
             Statement statement = admin.createStatement()) {
            statement.execute("CREATE DATABASE " + name);
        }
        return new ServerDatabase(name);
    }

    /**
     * Arrete ({@code kill}, {@code stop}) ou relance ({@code start}) le serveur.
     */
    static void control(String action) throws IOException, InterruptedException {
        Process process = new ProcessBuilder("bash", setting("ctl"), product(), action).redirectErrorStream(true)
                .start();
        if (!process.waitFor(120, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("db-server.sh " + action + " : delai depasse");
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.exitValue() != 0) {
            throw new IllegalStateException("db-server.sh " + action + " en echec : " + output);
        }
    }

    static Driver driver() {
        try {
            return DriverManager.getDriver("jdbc:mariadb:");
        } catch (SQLException failure) {
            throw new IllegalStateException("Pilote MariaDB absent du classpath de test", failure);
        }
    }

    /**
     * @return le mot de passe de test (pour verifier qu'il n'apparait dans aucun message)
     */
    static String password() {
        return setting("password") == null ? "" : setting("password");
    }

    String name() {
        return name;
    }

    /**
     * Options du pilote propres au serveur teste : MySQL 8.4 exige SSL (ou la recuperation de sa
     * cle publique) pour {@code caching_sha2_password} ; ses certificats sont generes au premier
     * demarrage, d'ou {@code sslMode=trust} (chiffre, certificat non verifie).
     */
    static Map<String, String> productOptions() {
        return "mysql".equals(product()) ? Map.of("sslMode", "trust") : Map.of();
    }

    ServerConnection connection(Map<String, String> options) {
        return new ServerConnection(setting("host"), Integer.parseInt(setting("port")), name, setting("user"),
                password(), options);
    }

    HikariDataSource pool() {
        return pool(DIALECT, POOL);
    }

    HikariDataSource pool(MariaDbDialect dialect, PoolOptions options) {
        return ConnectionPools.server("NovaBank-IT", connection(productOptions()), driver(), dialect, options);
    }

    SqlStorage open(String prefix) {
        return open(pool(), prefix);
    }

    SqlStorage open(DataSource dataSource, String prefix) {
        return open(dataSource, DIALECT, prefix);
    }

    SqlStorage open(DataSource dataSource, MariaDbDialect dialect, String prefix) {
        return SqlStorage.open(dataSource, dialect, prefix, Clock.systemUTC());
    }

    /**
     * @return une connexion directe a la base, hors pool, avec les reglages de session du dialecte
     */
    Connection rawConnection() throws SQLException {
        return unpooled(name, DIALECT).getConnection();
    }

    /** Supprime la base. */
    @Override
    public void close() throws SQLException {
        try (Connection admin = unpooled(setting("name"), DIALECT).getConnection();
             Statement statement = admin.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + name);
        }
    }

    private static DriverDataSource unpooled(String database, MariaDbDialect dialect) {
        Properties properties = new Properties();
        properties.putAll(productOptions());
        properties.putAll(dialect.connectionProperties());
        properties.setProperty("user", setting("user"));
        properties.setProperty("password", password());
        return new DriverDataSource(driver(), dialect.jdbcUrl(setting("host"), Integer.parseInt(setting("port")),
                database), properties, dialect.connectionInitStatements());
    }

    private static String setting(String key) {
        String value = System.getProperty("novabank.it.db." + key);
        if (value == null || value.isBlank()) {
            value = System.getenv("NOVABANK_IT_DB_" + key.toUpperCase(Locale.ROOT));
        }
        return value == null || value.isBlank() ? null : value;
    }
}
