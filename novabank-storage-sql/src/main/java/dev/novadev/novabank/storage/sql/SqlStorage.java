package dev.novadev.novabank.storage.sql;

import dev.novadev.novabank.core.delivery.PendingDeliveryStore;
import dev.novadev.novabank.core.repository.BankRepository;
import dev.novadev.novabank.core.repository.StorageException;
import dev.novadev.novabank.core.selection.ActiveBankStore;
import dev.novadev.novabank.core.storage.StorageBackend;
import dev.novadev.novabank.core.storage.StorageContent;
import dev.novadev.novabank.core.sync.BankChangeFeed;
import dev.novadev.novabank.core.text.ConsoleText;
import dev.novadev.novabank.storage.sql.dialect.SqlDialect;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import javax.sql.DataSource;

/**
 * Point d'entree du stockage SQL ({@link StorageBackend}) : migre le schema a l'ouverture et
 * expose les ports du coeur. Proprietaire de la {@link DataSource} recue : {@link #close()} la ferme si elle
 * est fermable (pool HikariCP).
 */
public final class SqlStorage implements StorageBackend {

    /** Prefixe des tables par defaut. */
    public static final String DEFAULT_TABLE_PREFIX = "novabank_";

    private final DataSource dataSource;
    private final int schemaVersion;
    private final SchemaMigrator migrator;
    private final BankRepository banks;
    private final PendingDeliveryStore deliveries;
    private final ActiveBankStore activeBanks;
    private final StorageContent content;

    private SqlStorage(DataSource dataSource, int schemaVersion, SchemaMigrator migrator, BankRepository banks,
                       PendingDeliveryStore deliveries, ActiveBankStore activeBanks, StorageContent content) {
        this.dataSource = dataSource;
        this.schemaVersion = schemaVersion;
        this.migrator = migrator;
        this.banks = banks;
        this.deliveries = deliveries;
        this.activeBanks = activeBanks;
        this.content = content;
    }

    /**
     * Ouvre le stockage et amene le schema a la version courante. En cas d'echec, la
     * source de donnees est fermee.
     *
     * @param dataSource  source des connexions, dont le stockage devient proprietaire
     * @param dialect     dialecte du moteur
     * @param tablePrefix prefixe des tables, {@code [a-z][a-z0-9_]{0,31}}
     * @param clock       horloge des dates techniques (creation, mise a jour)
     * @return le stockage pret
     * @throws StorageException         si la base est inaccessible ou d'un schema plus recent
     * @throws IllegalArgumentException si le prefixe est invalide
     */
    public static SqlStorage open(DataSource dataSource, SqlDialect dialect, String tablePrefix, Clock clock) {
        Objects.requireNonNull(dataSource, "dataSource");
        Objects.requireNonNull(dialect, "dialect");
        Objects.requireNonNull(clock, "clock");
        try {
            TableNames tables = new TableNames(tablePrefix);
            SqlTransactions transactions = new SqlTransactions(dataSource, dialect);
            SchemaMigrator migrator = new SchemaMigrator(transactions, dialect, tables, clock);
            int version = migrator.migrate();
            BankRows bankRows = new BankRows(tables, dialect, clock);
            OperationRows operationRows = new OperationRows(tables, dialect);
            SqlPendingDeliveryStore deliveries = new SqlPendingDeliveryStore(transactions, tables, dialect, clock);
            SqlActiveBankStore activeBanks = new SqlActiveBankStore(transactions, tables, dialect, clock);
            return new SqlStorage(dataSource, version, migrator,
                    new SqlBankRepository(transactions, bankRows, operationRows), deliveries, activeBanks,
                    new SqlContent(transactions, tables, bankRows, operationRows, deliveries, activeBanks));
        } catch (RuntimeException failure) {
            closeAfterFailure(dataSource, failure);
            throw failure;
        }
    }

    /**
     * @return les banques et leur journal
     */
    @Override
    public BankRepository banks() {
        return banks;
    }

    /**
     * @return le flux de changements des banques (reconciliation multi-serveur) ; sans objet pour
     *         SQLite, dont le fichier ne se partage pas, mais lisible aussi pour les tests
     */
    @Override
    public Optional<BankChangeFeed> changeFeed() {
        return banks instanceof BankChangeFeed feed ? Optional.of(feed) : Optional.empty();
    }

    /**
     * @return la file des livraisons en attente
     */
    @Override
    public PendingDeliveryStore deliveries() {
        return deliveries;
    }

    /**
     * @return la banque active choisie par chaque joueur
     */
    @Override
    public ActiveBankStore activeBanks() {
        return activeBanks;
    }

    /**
     * @return le contenu complet, pour la migration entre backends
     */
    @Override
    public StorageContent content() {
        return content;
    }

    /**
     * @return la version de schema de la base
     */
    @Override
    public int schemaVersion() {
        return schemaVersion;
    }

    /**
     * Verifie que la base repond en relisant sa version de schema (vraie requete, a ne
     * pas appeler depuis un thread du serveur).
     *
     * @return la version de schema lue dans la base
     * @throws StorageException si la base est inaccessible
     */
    @Override
    public int checkSchemaVersion() {
        return migrator.currentVersion();
    }

    /**
     * Ferme la source de donnees (et donc le pool) si elle est fermable.
     *
     * @throws StorageException si la fermeture echoue
     */
    @Override
    public void close() {
        try {
            closeDataSource(dataSource);
        } catch (Exception failure) {
            throw new StorageException(ConsoleText.tr("Fermeture du stockage SQL impossible : {0}",
                    failure.getMessage()), failure);
        }
    }

    private static void closeAfterFailure(DataSource dataSource, RuntimeException failure) {
        try {
            closeDataSource(dataSource);
        } catch (Exception closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }

    private static void closeDataSource(DataSource dataSource) throws Exception {
        if (dataSource instanceof AutoCloseable closeable) {
            closeable.close();
        }
    }
}
