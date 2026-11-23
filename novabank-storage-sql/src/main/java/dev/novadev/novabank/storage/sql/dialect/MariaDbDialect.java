package dev.novadev.novabank.storage.sql.dialect;

import dev.novadev.novabank.core.money.Money;
import dev.novadev.novabank.core.text.ConsoleText;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Dialecte MariaDB et MySQL (InnoDB), utilise avec le pilote MariaDB Connector/J pour les deux
 * serveurs (decision D6). Aucune difference entre les deux moteurs n'est necessaire au SQL
 * emis : un seul dialecte, dont les scripts de migration sont dans {@code schema/mysql/}.
 *
 * <p>Choix :
 * <ul>
 *   <li>Plusieurs serveurs Minecraft ecrivent en parallele : aucune serialisation dans le
 *       processus ({@link #singleWriter()} faux). Le verrou est celui de la ligne de banque,
 *       pris par {@code SELECT ... FOR UPDATE} au debut de chaque transaction d'ecriture ; la
 *       version de banque et la cle primaire du journal restent les gardes-fous.</li>
 *   <li>Isolation {@code READ COMMITTED} : l'exactitude ne repose pas sur l'instantane de
 *       {@code REPEATABLE READ} (la seule lecture suivie d'une ecriture est verrouillee, donc
 *       toujours la derniere version validee), et {@code READ COMMITTED} supprime les verrous
 *       d'intervalle (gap locks) qu'InnoDB pose en {@code REPEATABLE READ} sur les lectures
 *       verrouillantes et les insertions : moins d'attentes et d'interblocages entre banques
 *       differentes.</li>
 *   <li>Interblocage (1213) et attente de verrou depassee (1205) : transaction annulee puis
 *       rejouee ({@link #isRetryable}). L'attente d'un verrou de ligne est bornee par
 *       {@code innodb_lock_wait_timeout} (10 s par defaut ici, 50 s sur le serveur).</li>
 *   <li>{@code sql_mode} strict : un montant hors de {@code DECIMAL(38,4)} ou une valeur
 *       invalide est une erreur, jamais une troncature silencieuse ;
 *       {@code NO_ENGINE_SUBSTITUTION} : une table ne peut pas etre creee hors InnoDB.</li>
 *   <li>Insertion sans effet sur doublon par {@code ON DUPLICATE KEY UPDATE c = c} plutot que
 *       {@code INSERT IGNORE}, qui transformerait aussi en avertissements les autres erreurs
 *       (contrainte {@code CHECK}, valeur tronquee).</li>
 *   <li>Upsert par {@code VALUES(c)} : compris par toutes les versions de MariaDB et de MySQL
 *       (5.7 a 8.4). MySQL 8.0.20+ le declare obsolete (avertissement serveur) au profit d'un
 *       alias que MariaDB ne connait pas.</li>
 *   <li>Propriete {@code useAffectedRows=false} imposee : un {@code UPDATE} compte les lignes
 *       trouvees, pas seulement les lignes changees (les controles {@code == 1} du code commun
 *       en dependent).</li>
 *   <li>Migration : DDL non transactionnel (validation implicite), donc verrou nomme
 *       {@code GET_LOCK} tenu pendant toute la migration et scripts rejouables
 *       ({@link #isAlreadyApplied}).</li>
 * </ul>
 */
public final class MariaDbDialect implements SqlDialect {

    /** Attente maximale d'un verrou de ligne par defaut. */
    public static final Duration DEFAULT_LOCK_WAIT_TIMEOUT = Duration.ofSeconds(10);

    /**
     * Attente maximale du verrou de migration par defaut : un autre serveur qui demarre en meme
     * temps migre en quelques secondes ; au-dela, il est probablement bloque.
     */
    public static final Duration DEFAULT_MIGRATION_LOCK_TIMEOUT = Duration.ofSeconds(120);

    private static final int ER_DUP_ENTRY = 1062;
    private static final int ER_DUP_ENTRY_WITH_KEY_NAME = 1586;
    private static final int ER_LOCK_WAIT_TIMEOUT = 1205;
    private static final int ER_LOCK_DEADLOCK = 1213;
    private static final Set<Integer> ALREADY_APPLIED = Set.of(
            1050, // ER_TABLE_EXISTS_ERROR
            1060, // ER_DUP_FIELDNAME : colonne deja ajoutee
            1061, // ER_DUP_KEYNAME : index deja cree
            1826); // ER_FK_DUP_NAME : cle etrangere deja creee

    /** Nom d'hote ou adresse IPv4 ; une adresse IPv6 est traitee a part. */
    private static final Pattern HOST = Pattern.compile("[A-Za-z0-9]([A-Za-z0-9._-]{0,252})");
    private static final Pattern IPV6 = Pattern.compile("[0-9A-Fa-f:.]{2,45}");
    private static final Pattern DATABASE = Pattern.compile("[A-Za-z0-9_]{1,64}");

    private final Duration lockWaitTimeout;
    private final Duration migrationLockTimeout;

    /** Dialecte avec les attentes par defaut. */
    public MariaDbDialect() {
        this(DEFAULT_LOCK_WAIT_TIMEOUT, DEFAULT_MIGRATION_LOCK_TIMEOUT);
    }

    /**
     * @param lockWaitTimeout      attente maximale d'un verrou de ligne (1 s minimum, secondes entieres)
     * @param migrationLockTimeout attente maximale du verrou de migration (1 s minimum)
     * @throws IllegalArgumentException si une attente est inferieure a 1 s
     */
    public MariaDbDialect(Duration lockWaitTimeout, Duration migrationLockTimeout) {
        this.lockWaitTimeout = requireAtLeastOneSecond(lockWaitTimeout, "lockWaitTimeout");
        this.migrationLockTimeout = requireAtLeastOneSecond(migrationLockTimeout, "migrationLockTimeout");
    }

    /**
     * URL JDBC du pilote MariaDB, valable pour un serveur MariaDB comme MySQL. Les identifiants
     * n'y figurent jamais : ils passent par les proprietes de connexion.
     *
     * @param host     nom d'hote, adresse IPv4 ou IPv6
     * @param port     port TCP
     * @param database nom de la base
     * @return l'URL JDBC
     * @throws IllegalArgumentException si un element risquerait de modifier l'URL
     *                                  (caracteres {@code / ? & @ ,} par exemple)
     */
    public String jdbcUrl(String host, int port, String database) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(database, "database");
        String address;
        if (HOST.matcher(host).matches()) {
            address = host;
        } else if (IPV6.matcher(host).matches() && host.contains(":")) {
            address = "[" + host + "]";
        } else {
            throw new IllegalArgumentException(ConsoleText.tr("Hote SQL invalide : '{0}'", host));
        }
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException(ConsoleText.tr("Port SQL invalide : {0}", port));
        }
        if (!DATABASE.matcher(database).matches()) {
            throw new IllegalArgumentException(ConsoleText.tr("Nom de base SQL invalide : '{0}'", database));
        }
        return "jdbc:mariadb://" + address + ":" + port + "/" + database;
    }

    /**
     * Conseil a joindre a un echec de connexion connu.
     *
     * <p>MySQL 8+ authentifie par defaut avec {@code caching_sha2_password} : sans SSL, le
     * pilote doit recevoir la cle publique RSA du serveur, ce qu'il refuse par defaut (une
     * cle substituee permettrait d'intercepter le mot de passe). NovaBank ne desactive pas
     * cette protection d'office : l'administrateur choisit.
     *
     * @param failureMessage message d'erreur du pilote
     * @return le conseil, ou vide si l'erreur n'est pas reconnue
     */
    public Optional<String> connectionFailureHint(String failureMessage) {
        if (failureMessage != null && failureMessage.contains("RSA public key is not available")) {
            return Optional.of("MySQL 8+ (caching_sha2_password) sans SSL : activez SSL (storage.sql.options :"
                    + " sslMode: verify-full avec le certificat du serveur, ou sslMode: trust) ou, sur un reseau"
                    + " de confiance uniquement, allowPublicKeyRetrieval: \"true\"");
        }
        return Optional.empty();
    }

    @Override
    public String name() {
        return "mysql";
    }

    @Override
    public Properties connectionProperties() {
        Properties properties = new Properties();
        properties.setProperty("useAffectedRows", "false");
        return properties;
    }

    @Override
    public List<String> connectionInitStatements() {
        return List.of(
                "SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED",
                "SET SESSION sql_mode = 'STRICT_ALL_TABLES,NO_ENGINE_SUBSTITUTION'",
                "SET SESSION innodb_lock_wait_timeout = " + lockWaitTimeout.toSeconds());
    }

    @Override
    public boolean singleWriter() {
        return false;
    }

    @Override
    public String tableOptions() {
        return " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin";
    }

    /**
     * Verrou nomme du serveur ({@code GET_LOCK}), lie a la session : libere par
     * {@code RELEASE_LOCK} ou automatiquement si la connexion est perdue (serveur Minecraft
     * arrete brutalement pendant la migration).
     */
    @Override
    public MigrationLock lockMigration(Connection connection, String name) throws SQLException {
        try (PreparedStatement lock = connection.prepareStatement("SELECT GET_LOCK(?, ?)")) {
            lock.setString(1, name);
            lock.setLong(2, migrationLockTimeout.toSeconds());
            try (ResultSet rows = lock.executeQuery()) {
                rows.next();
                int acquired = rows.getInt(1);
                if (rows.wasNull()) {
                    throw new SQLException(ConsoleText.tr("Verrou de migration '{0}' : erreur du serveur", name));
                }
                if (acquired != 1) {
                    throw new SQLException(
                            ConsoleText.tr("Verrou de migration '{0}' non obtenu en {1} s : un autre serveur migre-t-il la base ?",
                                    name, migrationLockTimeout.toSeconds()));
                }
            }
        }
        return () -> {
            try (PreparedStatement release = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
                release.setString(1, name);
                release.executeQuery().close();
            }
        };
    }

    @Override
    public String lockRowsSuffix() {
        return " FOR UPDATE";
    }

    @Override
    public String insertIgnoringDuplicate(String table, List<String> columns) {
        String first = columns.get(0);
        return SqlDialect.insert(table, columns) + " ON DUPLICATE KEY UPDATE " + first + " = " + first;
    }

    @Override
    public String upsert(String table, List<String> columns, List<String> keyColumns) {
        String updates = columns.stream()
                .filter(column -> !keyColumns.contains(column))
                .map(column -> column + " = VALUES(" + column + ")")
                .collect(Collectors.joining(", "));
        return SqlDialect.insert(table, columns) + " ON DUPLICATE KEY UPDATE " + updates;
    }

    @Override
    public void bindMoney(PreparedStatement statement, int index, BigDecimal amount) throws SQLException {
        if (amount == null) {
            statement.setNull(index, Types.DECIMAL);
        } else {
            statement.setBigDecimal(index, Money.of(amount));
        }
    }

    @Override
    public BigDecimal readMoney(ResultSet resultSet, String column) throws SQLException {
        BigDecimal amount = resultSet.getBigDecimal(column);
        return amount == null ? null : Money.of(amount);
    }

    @Override
    public boolean isUniqueViolation(SQLException failure) {
        int code = failure.getErrorCode();
        return code == ER_DUP_ENTRY || code == ER_DUP_ENTRY_WITH_KEY_NAME;
    }

    @Override
    public boolean isRetryable(SQLException failure) {
        int code = failure.getErrorCode();
        return code == ER_LOCK_DEADLOCK || code == ER_LOCK_WAIT_TIMEOUT;
    }

    @Override
    public boolean isAlreadyApplied(SQLException failure) {
        return ALREADY_APPLIED.contains(failure.getErrorCode());
    }

    private static Duration requireAtLeastOneSecond(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.compareTo(Duration.ofSeconds(1)) < 0) {
            throw new IllegalArgumentException(ConsoleText.tr("{0} inferieur a 1 s : {1}", name, value));
        }
        return value;
    }
}
