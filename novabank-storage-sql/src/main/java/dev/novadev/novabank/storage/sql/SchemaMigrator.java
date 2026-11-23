package dev.novadev.novabank.storage.sql;

import dev.novadev.novabank.core.repository.StorageException;
import dev.novadev.novabank.core.text.ConsoleText;
import dev.novadev.novabank.storage.sql.dialect.SqlDialect;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;

/**
 * Migrations de schema versionnees.
 *
 * <p>Chaque version {@code n} est un {@link SchemaScript} ; les versions appliquees sont
 * tracees dans {@code <prefixe>schema_version}. Relancer la migration sur une base a jour ne
 * fait rien ; une base dont le schema est plus recent que {@link #CURRENT_VERSION} est refusee.
 *
 * <p>Toute la migration se fait sur une seule connexion, sous le verrou de migration du
 * dialecte : deux serveurs Minecraft qui demarrent en meme temps sur la meme base ne migrent
 * jamais ensemble, le second attend puis constate que la base est a jour. Chaque version est
 * appliquee avec sa ligne de trace dans une transaction, qui relit la version courante.
 *
 * <p>Reprise d'une version interrompue : sous SQLite, le DDL est transactionnel, une version
 * interrompue est annulee en entier. Sous MySQL / MariaDB, chaque instruction DDL est validee
 * implicitement (et atomique depuis MySQL 8.0 / MariaDB 10.6) : une version interrompue (arret
 * brutal, connexion perdue) laisse ses premieres instructions appliquees sans ligne de trace.
 * Au demarrage suivant, la version est rejouee entierement : les scripts sont ecrits pour etre
 * rejouables ({@code CREATE TABLE IF NOT EXISTS}, index declares dans la table) et une
 * instruction dont l'objet existe deja ({@link SqlDialect#isAlreadyApplied}, par exemple une
 * colonne deja ajoutee) est consideree comme appliquee.
 */
final class SchemaMigrator {

    /** Version de schema attendue par ce code. */
    static final int CURRENT_VERSION = 3;

    private final SqlTransactions transactions;
    private final SqlDialect dialect;
    private final TableNames tables;
    private final Clock clock;

    SchemaMigrator(SqlTransactions transactions, SqlDialect dialect, TableNames tables, Clock clock) {
        this.transactions = transactions;
        this.dialect = dialect;
        this.tables = tables;
        this.clock = clock;
    }

    /**
     * Amene la base a {@link #CURRENT_VERSION}.
     *
     * @return la version de schema de la base apres migration
     * @throws StorageException si la base est plus recente que le code, si le verrou de
     *                          migration n'est pas obtenu, ou sur erreur SQL
     */
    int migrate() {
        return transactions.session("migration du schema", connection -> {
            SqlDialect.MigrationLock lock = dialect.lockMigration(connection, tables.prefix() + "migration");
            int version;
            try {
                version = migrateLocked(connection);
            } catch (SQLException | RuntimeException failure) {
                release(lock, failure);
                throw failure;
            }
            lock.close();
            return version;
        });
    }

    private int migrateLocked(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(versionTableDdl(tables.schemaVersion(), dialect));
        }
        int version = checkedVersion(connection);
        while (version < CURRENT_VERSION) {
            version = SqlTransactions.inTransaction(connection, this::applyNext);
        }
        return version;
    }

    private static void release(SqlDialect.MigrationLock lock, Exception failure) {
        try {
            lock.close();
        } catch (SQLException releaseFailure) {
            failure.addSuppressed(releaseFailure);
        }
    }

    /**
     * @param table   table des versions
     * @param dialect dialecte (options de table)
     * @return l'instruction qui cree la table des versions si elle n'existe pas
     */
    static String versionTableDdl(String table, SqlDialect dialect) {
        return "CREATE TABLE IF NOT EXISTS " + table + " (version INTEGER NOT NULL PRIMARY KEY,"
                + " applied_at BIGINT NOT NULL)" + dialect.tableOptions();
    }

    /**
     * @return la version de schema enregistree dans la base (0 si aucune)
     * @throws StorageException si la base est plus recente que le code, ou sur erreur SQL
     */
    int currentVersion() {
        return transactions.read("lecture de la version de schema", this::checkedVersion);
    }

    private int applyNext(Connection connection) throws SQLException {
        int next = checkedVersion(connection) + 1;
        if (next > CURRENT_VERSION) {
            return next - 1;
        }
        try (Statement statement = connection.createStatement()) {
            for (String sql : SchemaScript.statements(dialect.name(), next, tables.prefix())) {
                execute(statement, sql);
            }
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO " + tables.schemaVersion() + " (version, applied_at) VALUES (?, ?)")) {
            insert.setInt(1, next);
            insert.setLong(2, clock.millis());
            insert.executeUpdate();
        }
        return next;
    }

    private void execute(Statement statement, String sql) throws SQLException {
        try {
            statement.execute(sql);
        } catch (SQLException failure) {
            if (!dialect.isAlreadyApplied(failure)) {
                throw failure;
            }
            // Instruction appliquee par une migration interrompue : rien a refaire.
        }
    }

    private int checkedVersion(Connection connection) throws SQLException {
        int version;
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT MAX(version) FROM " + tables.schemaVersion())) {
            rows.next();
            version = rows.getInt(1);
        }
        if (version > CURRENT_VERSION) {
            throw new StorageException(
                    ConsoleText.tr("Le schema de la base (version {0}) est plus recent que celui de cette version de NovaBank (version {1}) : mettez NovaBank a jour ou restaurez une sauvegarde de la base.",
                            version, CURRENT_VERSION));
        }
        return version;
    }
}
