package dev.novadev.novabank.storage.sql;

import com.zaxxer.hikari.HikariDataSource;
import dev.novadev.novabank.storage.sql.dialect.SqliteDialect;
import dev.novadev.novabank.storage.sql.pool.ConnectionPools;
import dev.novadev.novabank.storage.sql.pool.DriverDataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Clock;
import javax.sql.DataSource;

/**
 * Ouverture de bases SQLite reelles pour les tests.
 *
 * <p>Le pilote est obtenu par {@link DriverManager} (present sur le classpath de test) puis
 * transmis comme le fera la plateforme avec le pilote telecharge : aucune classe
 * {@code org.sqlite} n'est nommee.
 */
final class SqliteTestSupport {

    static final SqliteDialect DIALECT = new SqliteDialect();

    private SqliteTestSupport() {
    }

    static Driver driver() {
        try {
            return DriverManager.getDriver("jdbc:sqlite:");
        } catch (SQLException failure) {
            throw new IllegalStateException("Pilote SQLite absent du classpath de test", failure);
        }
    }

    static HikariDataSource pool(Path file) {
        return ConnectionPools.sqlite(file, driver(), DIALECT, ConnectionPools.DEFAULT_SQLITE_POOL_SIZE);
    }

    static SqlStorage open(Path file) {
        return open(pool(file));
    }

    static SqlStorage open(DataSource dataSource) {
        return SqlStorage.open(dataSource, DIALECT, SqlStorage.DEFAULT_TABLE_PREFIX, Clock.systemUTC());
    }

    /**
     * Connexion directe, hors pool, avec les reglages de session du dialecte.
     */
    static Connection rawConnection(Path file) throws SQLException {
        return unpooled(file, DIALECT).getConnection();
    }

    static DriverDataSource unpooled(Path file, SqliteDialect dialect) {
        return new DriverDataSource(driver(), dialect.jdbcUrl(file), dialect.connectionProperties(),
                dialect.connectionInitStatements());
    }
}
