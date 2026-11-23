package dev.novadev.novabank.core.sync;

import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.core.delivery.PendingDeliveryStore;
import dev.novadev.novabank.core.repository.BankRepository;
import dev.novadev.novabank.core.selection.ActiveBankStore;
import dev.novadev.novabank.core.storage.FreezableBackend;
import dev.novadev.novabank.core.storage.StorageContent;
import dev.novadev.novabank.core.storage.StorageBackend;
import dev.novadev.novabank.core.testing.InMemoryStorageBackend;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class PublishingBackendTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-28T12:00:00Z"), ZoneOffset.UTC);

    private static SyncCoordinator coordinator() {
        Logger quiet = Logger.getLogger("publishing-backend-test");
        quiet.setLevel(Level.OFF);
        return new SyncCoordinator("srv", (since, limit) -> List.of(), null, change -> { }, CLOCK, SyncTiming.defaults(),
                quiet);
    }

    @Test
    void banksAreDecoratedAndEverythingElseIsDelegated() {
        InMemoryStorageBackend real = new InMemoryStorageBackend();
        PublishingBackend backend = new PublishingBackend(real, coordinator(), CLOCK);

        assertThat(backend.banks()).isInstanceOf(PublishingBankRepository.class).isNotSameAs(real.banks());
        assertThat(backend.deliveries()).isSameAs(real.deliveries());
        assertThat(backend.activeBanks()).isSameAs(real.activeBanks());
        assertThat(backend.schemaVersion()).isEqualTo(real.schemaVersion());
        assertThat(backend.changeFeed()).isEmpty();
    }

    @Test
    void closingTheDecoratorLeavesTheRealBackendToItsOwner() {
        StorageBackend real = new InMemoryStorageBackend();
        PublishingBackend backend = new PublishingBackend(real, coordinator(), CLOCK);

        backend.close();

        assertThat(real.checkSchemaVersion()).isEqualTo(real.schemaVersion());
    }

    @Test
    void theChangeFeedSurvivesTheFreezableWrapper() {
        BankChangeFeed feed = (since, limit) -> List.of();
        StorageBackend withFeed = new FeedBackend(feed);

        assertThat(new FreezableBackend(withFeed).changeFeed()).containsSame(feed);
        assertThat(new FreezableBackend(new InMemoryStorageBackend()).changeFeed()).isEmpty();
    }

    /** Backend en memoire qui expose un flux de changements. */
    private static final class FeedBackend implements StorageBackend {
        private final InMemoryStorageBackend real = new InMemoryStorageBackend();
        private final BankChangeFeed feed;

        FeedBackend(BankChangeFeed feed) {
            this.feed = feed;
        }

        @Override
        public BankRepository banks() {
            return real.banks();
        }

        @Override
        public PendingDeliveryStore deliveries() {
            return real.deliveries();
        }

        @Override
        public ActiveBankStore activeBanks() {
            return real.activeBanks();
        }

        @Override
        public StorageContent content() {
            return real.content();
        }

        @Override
        public Optional<BankChangeFeed> changeFeed() {
            return Optional.of(feed);
        }

        @Override
        public int schemaVersion() {
            return real.schemaVersion();
        }

        @Override
        public int checkSchemaVersion() {
            return real.checkSchemaVersion();
        }

        @Override
        public void close() {
            real.close();
        }
    }
}
