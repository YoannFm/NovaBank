package dev.novadev.novabank.storage.sql.pool;

import dev.novadev.novabank.core.text.ConsoleText;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.logging.Logger;
import javax.sql.DataSource;

/**
 * {@link DataSource} non poolee qui ouvre ses connexions avec une instance de
 * {@link Driver} fournie par l'appelant.
 *
 * <p>C'est le point d'extension du chargement des pilotes : la plateforme charge le pilote
 * telecharge (decision D6) dans son propre chargeur de classes et transmet l'instance.
 * Ce module ne nomme jamais une classe de pilote et ne passe pas par
 * {@code DriverManager}, qui ne voit pas les pilotes charges hors du chargeur systeme.
 * Chaque nouvelle connexion recoit les instructions d'initialisation du dialecte.
 */
public final class DriverDataSource implements DataSource {

    private final Driver driver;
    private final String url;
    private final Properties properties;
    private final List<String> initStatements;
    private volatile int loginTimeoutSeconds;
    private volatile PrintWriter logWriter;

    /**
     * @param driver         pilote JDBC
     * @param url            URL JDBC acceptee par ce pilote
     * @param properties     proprietes de connexion (copiees)
     * @param initStatements instructions executees sur chaque nouvelle connexion
     */
    public DriverDataSource(Driver driver, String url, Properties properties, List<String> initStatements) {
        this.driver = Objects.requireNonNull(driver, "driver");
        this.url = Objects.requireNonNull(url, "url");
        this.properties = new Properties();
        this.properties.putAll(Objects.requireNonNull(properties, "properties"));
        this.initStatements = List.copyOf(initStatements);
    }

    @Override
    public Connection getConnection() throws SQLException {
        Properties copy = new Properties();
        copy.putAll(properties);
        Connection connection = driver.connect(url, copy);
        if (connection == null) {
            throw new SQLException(ConsoleText.tr("Le pilote {0} ne reconnait pas l'URL {1}",
                    driver.getClass().getName(), url));
        }
        try (Statement statement = connection.createStatement()) {
            for (String sql : initStatements) {
                statement.execute(sql);
            }
        } catch (SQLException | RuntimeException failure) {
            try {
                connection.close();
            } catch (SQLException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
        return connection;
    }

    /**
     * Non supporte : les identifiants font partie des proprietes de connexion.
     *
     * @throws SQLFeatureNotSupportedException toujours
     */
    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        throw new SQLFeatureNotSupportedException(
                ConsoleText.tr("Identifiants a passer dans les proprietes de connexion"));
    }

    @Override
    public PrintWriter getLogWriter() {
        return logWriter;
    }

    @Override
    public void setLogWriter(PrintWriter out) {
        this.logWriter = out;
    }

    @Override
    public void setLoginTimeout(int seconds) {
        this.loginTimeoutSeconds = seconds;
    }

    @Override
    public int getLoginTimeout() {
        return loginTimeoutSeconds;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException(ConsoleText.tr("Pas de journalisation java.util.logging"));
    }

    @Override
    public <T> T unwrap(Class<T> type) throws SQLException {
        if (type.isInstance(this)) {
            return type.cast(this);
        }
        throw new SQLException(ConsoleText.tr("Pas d'implementation de {0}", type.getName()));
    }

    @Override
    public boolean isWrapperFor(Class<?> type) {
        return type.isInstance(this);
    }
}
