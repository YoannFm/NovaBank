package dev.novadev.novabank.storage.sql.dialect;

import dev.novadev.novabank.core.money.Money;
import dev.novadev.novabank.core.text.ConsoleText;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * Dialecte SQLite (3.24 ou plus, pour {@code ON CONFLICT}).
 *
 * <p>Reglages de session :
 * <ul>
 *   <li>{@code journal_mode=WAL} : les lectures ne bloquent pas l'ecrivain et inversement ;</li>
 *   <li>{@code synchronous=FULL} : le WAL est synchronise sur disque a chaque commit. En
 *       WAL, {@code NORMAL} protege deja de la corruption et d'un arret brutal du processus,
 *       mais peut perdre les derniers commits sur coupure de courant ou plantage du systeme.
 *       Un retrait confirme puis perdu rendrait des items deja remis : la durabilite de
 *       chaque commit est une regle anti-duplication, son cout (une synchronisation par
 *       operation) est negligeable au debit d'un serveur Minecraft ;</li>
 *   <li>{@code foreign_keys=ON} : coherence entre banques, lignes de stock et journal ;</li>
 *   <li>{@code busy_timeout} : attente d'un verrou tenu par un autre processus au lieu
 *       d'un echec immediat.</li>
 * </ul>
 *
 * <p>Verrouillage : propriete de pilote {@code transaction_mode=IMMEDIATE}, chaque
 * transaction prend le verrou d'ecriture des son ouverture. Sans cela, une transaction
 * qui lit puis ecrit apres le commit d'un autre ecrivain echoue en WAL avec
 * {@code SQLITE_BUSY} sans attendre. Les ecritures sont en plus serialisees dans le
 * processus ({@link #singleWriter()}) : file d'attente equitable, aucune attente active
 * du verrou SQLite.
 *
 * <p>Montants : stockes en {@code TEXT} canonique. Une colonne {@code DECIMAL} aurait sous
 * SQLite l'affinite {@code NUMERIC}, qui convertit en {@code REAL} et perd des chiffres.
 */
public final class SqliteDialect implements SqlDialect {

    /** Attente par defaut d'un verrou tenu par un autre processus. */
    public static final Duration DEFAULT_BUSY_TIMEOUT = Duration.ofSeconds(10);

    private static final int SQLITE_CONSTRAINT = 19;
    private static final int PRIMARY_CODE_MASK = 0xFF;

    private final Duration busyTimeout;

    /** Dialecte avec l'attente de verrou par defaut. */
    public SqliteDialect() {
        this(DEFAULT_BUSY_TIMEOUT);
    }

    /**
     * @param busyTimeout attente maximale d'un verrou tenu par un autre processus
     * @throws IllegalArgumentException si l'attente est negative
     */
    public SqliteDialect(Duration busyTimeout) {
        this.busyTimeout = Objects.requireNonNull(busyTimeout, "busyTimeout");
        if (busyTimeout.isNegative()) {
            throw new IllegalArgumentException(ConsoleText.tr("Attente de verrou negative : {0}", busyTimeout));
        }
    }

    /**
     * @param file fichier de base
     * @return l'URL JDBC du fichier
     */
    public String jdbcUrl(Path file) {
        return "jdbc:sqlite:" + file.toAbsolutePath();
    }

    @Override
    public String name() {
        return "sqlite";
    }

    @Override
    public Properties connectionProperties() {
        Properties properties = new Properties();
        properties.setProperty("transaction_mode", "IMMEDIATE");
        return properties;
    }

    @Override
    public List<String> connectionInitStatements() {
        return List.of(
                "PRAGMA busy_timeout = " + busyTimeout.toMillis(),
                "PRAGMA journal_mode = WAL",
                "PRAGMA synchronous = FULL",
                "PRAGMA foreign_keys = ON");
    }

    @Override
    public boolean singleWriter() {
        return true;
    }

    @Override
    public String tableOptions() {
        return "";
    }

    /**
     * Aucun verrou supplementaire : chaque version est appliquee dans une transaction
     * {@code IMMEDIATE} qui relit la version courante, donc deux processus se serialisent sur
     * le verrou d'ecriture du fichier.
     */
    @Override
    public MigrationLock lockMigration(Connection connection, String name) {
        return MigrationLock.NONE;
    }

    @Override
    public String lockRowsSuffix() {
        return "";
    }

    @Override
    public String insertIgnoringDuplicate(String table, List<String> columns) {
        return SqlDialect.insert(table, columns) + " ON CONFLICT DO NOTHING";
    }

    @Override
    public String upsert(String table, List<String> columns, List<String> keyColumns) {
        String updates = columns.stream()
                .filter(column -> !keyColumns.contains(column))
                .map(column -> column + " = excluded." + column)
                .collect(Collectors.joining(", "));
        return SqlDialect.insert(table, columns) + " ON CONFLICT (" + String.join(", ", keyColumns) + ") DO UPDATE SET "
                + updates;
    }

    @Override
    public void bindMoney(PreparedStatement statement, int index, BigDecimal amount) throws SQLException {
        if (amount == null) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, Money.of(amount).toPlainString());
        }
    }

    @Override
    public BigDecimal readMoney(ResultSet resultSet, String column) throws SQLException {
        String text = resultSet.getString(column);
        return text == null ? null : Money.of(new BigDecimal(text));
    }

    @Override
    public boolean isUniqueViolation(SQLException failure) {
        // Code primaire SQLITE_CONSTRAINT (le pilote peut renvoyer le code etendu) ; le
        // message distingue l'unicite (cle primaire comprise) des CHECK et cles etrangeres.
        return (failure.getErrorCode() & PRIMARY_CODE_MASK) == SQLITE_CONSTRAINT
                && String.valueOf(failure.getMessage()).contains("UNIQUE constraint failed");
    }

    /**
     * Jamais : les ecritures sont serialisees et l'attente du verrou de fichier est deja
     * geree par {@code busy_timeout}.
     */
    @Override
    public boolean isRetryable(SQLException failure) {
        return false;
    }

    /**
     * Jamais : le DDL de SQLite est transactionnel, une version interrompue est annulee en entier.
     */
    @Override
    public boolean isAlreadyApplied(SQLException failure) {
        return false;
    }
}
