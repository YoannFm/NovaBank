package dev.novadev.novabank.storage.mongo;

import dev.novadev.novabank.core.delivery.PendingDeliveryStore;
import dev.novadev.novabank.core.delivery.PendingDeliveryStoreContractTest;
import dev.novadev.novabank.core.repository.BankRepository;
import dev.novadev.novabank.core.repository.BankRepositoryContractTest;
import dev.novadev.novabank.core.selection.ActiveBankStore;
import dev.novadev.novabank.core.selection.ActiveBankStoreContractTest;
import dev.novadev.novabank.core.sync.BankChangeFeedContractTest;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;

/**
 * Contrats complets du coeur sur un vrai serveur MongoDB : strategie automatique (transactions sur
 * replica set, journal en attente sur serveur autonome) et journal en attente impose (protocole
 * sans transaction verifie aussi sur replica set).
 */
@RequiresMongoServer
class MongoContractTests {

    private MongoTestServer server;

    @AfterEach
    void dropDatabase() {
        if (server != null) {
            server.close();
            server = null;
        }
    }

    private MongoBackend backend(MongoBackend.WriteMode mode) {
        if (server == null) {
            server = MongoTestServer.create();
        }
        return server.open(mode, Duration.ofSeconds(60));
    }

    @Nested
    class AutomaticBankRepository extends BankRepositoryContractTest {
        @Override
        protected BankRepository newRepository() {
            return backend(MongoBackend.WriteMode.AUTO).banks();
        }
    }

    @Nested
    class PendingProtocolBankRepository extends BankRepositoryContractTest {
        @Override
        protected BankRepository newRepository() {
            return backend(MongoBackend.WriteMode.WITHOUT_TRANSACTIONS).banks();
        }
    }

    @Nested
    class ChangeFeedAutomatic extends BankChangeFeedContractTest {
        @Override
        protected FeedFixture newFixture() {
            MongoBackend backend = backend(MongoBackend.WriteMode.AUTO);
            return new FeedFixture(backend.banks(), backend.changeFeed().orElseThrow());
        }
    }

    @Nested
    class ChangeFeedPendingProtocol extends BankChangeFeedContractTest {
        @Override
        protected FeedFixture newFixture() {
            MongoBackend backend = backend(MongoBackend.WriteMode.WITHOUT_TRANSACTIONS);
            return new FeedFixture(backend.banks(), backend.changeFeed().orElseThrow());
        }
    }

    @Nested
    class Deliveries extends PendingDeliveryStoreContractTest {
        @Override
        protected PendingDeliveryStore newStore() {
            return backend(MongoBackend.WriteMode.AUTO).deliveries();
        }
    }

    @Nested
    class ActiveBanks extends ActiveBankStoreContractTest {
        @Override
        protected ActiveBankStore newStore() {
            return backend(MongoBackend.WriteMode.AUTO).activeBanks();
        }
    }
}
