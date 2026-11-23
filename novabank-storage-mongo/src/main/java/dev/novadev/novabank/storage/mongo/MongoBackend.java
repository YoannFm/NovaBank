package dev.novadev.novabank.storage.mongo;

import com.mongodb.MongoClientSettings;
import com.mongodb.MongoException;
import com.mongodb.ReadPreference;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import dev.novadev.novabank.core.delivery.PendingDeliveryStore;
import dev.novadev.novabank.core.repository.BankRepository;
import dev.novadev.novabank.core.repository.StorageException;
import dev.novadev.novabank.core.selection.ActiveBankStore;
import dev.novadev.novabank.core.storage.StorageBackend;
import dev.novadev.novabank.core.storage.StorageContent;
import dev.novadev.novabank.core.sync.BankChangeFeed;
import dev.novadev.novabank.core.text.ConsoleText;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Backend MongoDB ouvert : client du pilote, schema verifie, ports du coeur. Proprietaire du
 * client, ferme par {@link #close()}.
 *
 * <p>Ecritures acquittees seulement une fois journalisees ({@link ServerTopology#durableWriteConcern()} :
 * majorite journalisee sur replica set, {@code w: 1, j: true} sur serveur autonome) : une operation
 * annoncee reussie survit a un arret brutal du serveur.
 */
public final class MongoBackend implements StorageBackend {

    /** Strategie d'ecriture. */
    enum WriteMode {
        /** Transactions si le serveur les supporte, journal en attente sinon (production). */
        AUTO,
        /** Journal en attente meme si le serveur supporte les transactions (tests du protocole). */
        WITHOUT_TRANSACTIONS
    }

    private final MongoClient client;
    private final MongoSchema schema;
    private final int schemaVersion;
    private final MongoBankRepository banks;
    private final PendingDeliveryStore deliveries;
    private final ActiveBankStore activeBanks;
    private final StorageContent content;
    private final MongoFailures failures;
    private final Logger logger;

    private MongoBackend(MongoClient client, MongoSchema schema, int schemaVersion, MongoBankRepository banks,
                         PendingDeliveryStore deliveries, ActiveBankStore activeBanks, StorageContent content,
                         MongoFailures failures, Logger logger) {
        this.client = client;
        this.schema = schema;
        this.schemaVersion = schemaVersion;
        this.banks = banks;
        this.deliveries = deliveries;
        this.activeBanks = activeBanks;
        this.content = content;
        this.failures = failures;
        this.logger = logger;
    }

    /**
     * Ouvre le backend : connexion, detection des transactions, schema, rattrapage des operations
     * en attente. En cas d'echec, le client est ferme.
     *
     * @param settings reglages de connexion
     * @param clock    horloge des dates techniques
     * @param logger   journal
     * @return le backend pret
     * @throws StorageException si le serveur est injoignable ou porte un schema plus recent
     */
    public static MongoBackend open(MongoConnectionSettings settings, Clock clock, Logger logger) {
        return open(settings, clock, logger, Contention.DEFAULT_BUDGET, WriteMode.AUTO);
    }

    static MongoBackend open(MongoConnectionSettings settings, Clock clock, Logger logger, Duration contentionBudget,
                             WriteMode mode) {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(logger, "logger");
        MongoFailures failures = new MongoFailures(settings);
        MongoClient client = MongoClients.create(clientSettings(settings));
        try {
            ServerTopology topology = ServerTopology.detect(client.getDatabase("admin"));
            MongoDatabase database = client.getDatabase(settings.database())
                    .withWriteConcern(topology.durableWriteConcern());
            MongoCollections collections = MongoCollections.of(database);
            MongoSchema schema = new MongoSchema(collections, clock);
            int version = schema.migrate();
            BankDocuments bankDocuments = new BankDocuments(clock);
            PendingOperations pending = new PendingOperations(collections, bankDocuments, clock, logger);
            Contention contention = new Contention(contentionBudget);
            BankWrites writes = mode == WriteMode.AUTO && topology.supportsTransactions()
                    ? new TransactionalBankWrites(client, collections, bankDocuments, pending)
                    : new PendingBankWrites(collections, bankDocuments, pending);
            MongoBankRepository banks = new MongoBankRepository(collections, bankDocuments, pending, writes, contention,
                    failures);
            logger.info(topology.describe());
            int recovered = banks.recover();
            if (recovered > 0) {
                logger.warning(ConsoleText.tr("MongoDB : {0} operation(s) en attente rattrapee(s) au demarrage",
                        recovered));
            }
            return new MongoBackend(client, schema, version, banks,
                    new MongoPendingDeliveryStore(collections, clock, failures),
                    new MongoActiveBankStore(collections, clock, failures),
                    new MongoContent(collections, bankDocuments, banks, failures, clock), failures, logger);
        } catch (MongoException failure) {
            closeQuietly(client, logger, failure);
            throw failures.storage("ouverture de " + settings.describe(), failure);
        } catch (RuntimeException failure) {
            closeQuietly(client, logger, failure);
            throw failure;
        }
    }

    /**
     * Ferme un client dont l'ouverture du backend a echoue, avec l'elagueur de tampons du pilote
     * (sinon il retiendrait le chargeur du module) ; une erreur de fermeture est rattachee a
     * l'echec d'origine.
     */
    private static void closeQuietly(MongoClient client, Logger logger, RuntimeException failure) {
        try {
            client.close();
        } catch (RuntimeException closeFailure) {
            failure.addSuppressed(closeFailure);
        } finally {
            DriverThreads.stopBufferPoolPruner(logger);
        }
    }

    private static MongoClientSettings clientSettings(MongoConnectionSettings settings) {
        return MongoClientSettings.builder()
                .applyConnectionString(settings.uri())
                .applicationName("NovaBank")
                .applyToSocketSettings(socket -> socket
                        .connectTimeout(settings.connectTimeoutMs(), TimeUnit.MILLISECONDS)
                        .readTimeout(settings.socketTimeoutMs(), TimeUnit.MILLISECONDS))
                .applyToClusterSettings(cluster -> cluster
                        .serverSelectionTimeout(settings.serverSelectionTimeoutMs(), TimeUnit.MILLISECONDS))
                .readPreference(ReadPreference.primary())
                .retryWrites(true)
                .build();
    }

    @Override
    public Optional<BankChangeFeed> changeFeed() {
        return Optional.of(banks);
    }

    @Override
    public BankRepository banks() {
        return banks;
    }

    @Override
    public PendingDeliveryStore deliveries() {
        return deliveries;
    }

    @Override
    public ActiveBankStore activeBanks() {
        return activeBanks;
    }

    @Override
    public StorageContent content() {
        return content;
    }

    @Override
    public int schemaVersion() {
        return schemaVersion;
    }

    @Override
    public int checkSchemaVersion() {
        try {
            return schema.currentVersion();
        } catch (MongoException failure) {
            throw failures.storage("lecture de la version de schema", failure);
        }
    }

    /**
     * Ferme le client (threads de surveillance et pool de connexions), puis l'elagueur de tampons
     * partage du pilote ({@link DriverThreads}).
     */
    @Override
    public void close() {
        try {
            client.close();
        } catch (RuntimeException failure) {
            throw new StorageException(ConsoleText.tr("Fermeture du client MongoDB impossible : {0}",
                    failures.mask(failure.getMessage())),
                    failure);
        } finally {
            DriverThreads.stopBufferPoolPruner(logger);
        }
    }
}
