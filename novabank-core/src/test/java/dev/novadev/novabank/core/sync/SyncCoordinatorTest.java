package dev.novadev.novabank.core.sync;

import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.api.OperationId;
import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.api.valuation.ValuationMode;
import dev.novadev.novabank.core.journal.OperationRecord;
import dev.novadev.novabank.core.repository.ApplyOutcome;
import dev.novadev.novabank.core.repository.MutationDecision;
import dev.novadev.novabank.core.repository.StorageException;
import dev.novadev.novabank.core.testing.InMemoryBankRepository;
import dev.novadev.novabank.core.testing.ManualScheduler;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Coordinateur de synchronisation : bus et lecture du stockage se completent sans doublon.
 */
class SyncCoordinatorTest {

    private static final Instant START = Instant.parse("2026-10-28T12:00:00Z");
    private static final BankId BANK_A = new BankId(UUID.fromString("00000000-0000-0000-0000-00000000000a"));
    private static final BankId BANK_B = new BankId(UUID.fromString("00000000-0000-0000-0000-00000000000b"));

    private final List<BankChange> delivered = new ArrayList<>();
    private final FakeFeed feed = new FakeFeed();
    private final FakeBus bus = new FakeBus();
    private final MutableClock clock = new MutableClock(START);
    private SyncCoordinator coordinator;

    @BeforeEach
    void setUp() {
        coordinator = coordinator(bus, SyncTiming.defaults());
    }

    private SyncCoordinator coordinator(FakeBus withBus, SyncTiming timing) {
        Logger quiet = Logger.getLogger("sync-test-" + UUID.randomUUID());
        quiet.setLevel(Level.OFF);
        SyncCoordinator created = new SyncCoordinator("srv-1", feed, withBus, delivered::add, clock, timing, quiet);
        created.start(new ManualScheduler());
        return created;
    }

    private static BankChange change(BankId bank, long version, Instant at) {
        return new BankChange(bank, version, 0L, at);
    }

    @Test
    void localChangeIsPublishedButNeverDeliveredToTheLocalListener() {
        coordinator.localChange(change(BANK_A, 1, START));

        assertThat(bus.published).hasSize(1);
        assertThat(bus.published.get(0).originServer()).isEqualTo("srv-1");
        assertThat(delivered).isEmpty();
        assertThat(coordinator.stats().published()).isEqualTo(1);
    }

    @Test
    void ownWriteSeenInTheStorageFeedIsNotDeliveredBack() {
        coordinator.localChange(change(BANK_A, 3, START.plusSeconds(1)));
        feed.add(change(BANK_A, 3, START.plusSeconds(1)));

        coordinator.poll();

        assertThat(delivered).isEmpty();
    }

    @Test
    void remoteBusEventIsDeliveredOnceAndStaleOrOwnEventsAreIgnored() {
        bus.emit(new SyncEvent("srv-2", change(BANK_A, 5, START)));
        bus.emit(new SyncEvent("srv-2", change(BANK_A, 5, START)));
        bus.emit(new SyncEvent("srv-2", change(BANK_A, 4, START)));
        bus.emit(new SyncEvent("srv-1", change(BANK_A, 9, START)));
        bus.emit(new SyncEvent("srv-3", change(BANK_A, 6, START)));

        assertThat(delivered).extracting(BankChange::version).containsExactly(5L, 6L);
        assertThat(coordinator.stats().fromBus()).isEqualTo(2);
    }

    @Test
    void storagePollCatchesWhatTheBusLostAndDoesNotRepeatWhatTheBusDelivered() {
        bus.emit(new SyncEvent("srv-2", change(BANK_A, 1, START.plusSeconds(1))));
        feed.add(change(BANK_A, 1, START.plusSeconds(1)));
        feed.add(change(BANK_B, 7, START.plusSeconds(2)));

        coordinator.poll();
        coordinator.poll();

        assertThat(delivered).extracting(BankChange::bankId, BankChange::version)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(BANK_A, 1L),
                        org.assertj.core.groups.Tuple.tuple(BANK_B, 7L));
        assertThat(coordinator.stats().fromStorage()).isEqualTo(1);
        assertThat(coordinator.stats().lastPoll()).isPresent();
    }

    @Test
    void worksWithoutAnyBusAndWhenTheBusRefusesToPublish() {
        bus.connected = false;
        coordinator.localChange(change(BANK_A, 1, START));
        assertThat(coordinator.stats().publishRefused()).isEqualTo(1);

        SyncCoordinator pollOnly = coordinator(null, SyncTiming.defaults());
        feed.add(change(BANK_B, 2, START.plusSeconds(1)));
        pollOnly.poll();
        pollOnly.localChange(change(BANK_A, 1, START));

        assertThat(delivered).extracting(BankChange::bankId).containsExactly(BANK_B);
        assertThat(pollOnly.busStatus()).isEmpty();
    }

    @Test
    void storageOutageIsReportedThenForgottenAndTheCursorDoesNotSkipChanges() {
        feed.fail = true;
        feed.add(change(BANK_A, 1, START.plusSeconds(1)));

        coordinator.poll();
        assertThat(coordinator.stats().lastPollError()).isPresent();
        assertThat(delivered).isEmpty();

        feed.fail = false;
        coordinator.poll();

        assertThat(coordinator.stats().lastPollError()).isEmpty();
        assertThat(delivered).extracting(BankChange::version).containsExactly(1L);
    }

    @Test
    void overlapRereadsChangesCommittedLateWithAnEarlierDate() {
        feed.add(change(BANK_A, 1, START.plusSeconds(100)));
        coordinator.poll();
        // Un autre serveur, horloge en retard de 20 s, valide une transaction apres coup.
        feed.add(change(BANK_B, 1, START.plusSeconds(80)));

        coordinator.poll();

        assertThat(delivered).extracting(BankChange::bankId).containsExactly(BANK_A, BANK_B);
    }

    @Test
    void pagesAreReadUntilExhausted() {
        SyncCoordinator paged = coordinator(null, new SyncTiming(Duration.ofSeconds(1), Duration.ofSeconds(30), 2));
        for (int i = 0; i < 5; i++) {
            feed.add(change(new BankId(new UUID(0, 100 + i)), 1, START.plusSeconds(1 + i)));
        }

        paged.poll();

        assertThat(delivered).hasSize(5);
    }

    @Test
    void aFailingListenerDoesNotBlockOtherBanks() {
        SyncCoordinator picky = new SyncCoordinator("srv-1", feed, null, change -> {
            if (change.bankId().equals(BANK_A)) {
                throw new IllegalStateException("boom");
            }
            delivered.add(change);
        }, clock, SyncTiming.defaults(), quietLogger());
        feed.add(change(BANK_A, 1, START.plusSeconds(1)));
        feed.add(change(BANK_B, 1, START.plusSeconds(2)));

        picky.poll();

        assertThat(delivered).extracting(BankChange::bankId).containsExactly(BANK_B);
    }

    @Test
    void publishingRepositoryAnnouncesAppliedMutationsOnly() {
        InMemoryBankRepository inner = new InMemoryBankRepository();
        PublishingBankRepository repository = new PublishingBankRepository(inner, coordinator, clock);
        BankId bank = new BankId(UUID.fromString("00000000-0000-0000-0000-0000000000c1"));
        repository.createBank(bank, new EntityRef(EntityRef.PLAYER, "p"), ValuationMode.DERIVED);
        ItemKey stone = ItemKey.minecraft("stone");
        OperationId id = OperationId.random();

        ApplyOutcome applied = repository.apply(id, bank, current -> MutationDecision.accept(
                current.withLine(stone, current.line(stone).plus(1L)),
                OperationRecord.deposit(id, bank, stone, 1L, 1L, BigDecimal.ONE, START)));
        ApplyOutcome replay = repository.apply(id, bank, current -> MutationDecision.accept(current, null));

        assertThat(applied).isInstanceOf(ApplyOutcome.Applied.class);
        assertThat(replay).isInstanceOf(ApplyOutcome.Duplicate.class);
        assertThat(bus.published).hasSize(1);
        assertThat(bus.published.get(0).change().bankId()).isEqualTo(bank);
        assertThat(bus.published.get(0).change().version()).isEqualTo(1L);
    }

    private static Logger quietLogger() {
        Logger quiet = Logger.getLogger("sync-test-" + UUID.randomUUID());
        quiet.setLevel(Level.OFF);
        return quiet;
    }

    private static final class FakeFeed implements BankChangeFeed {
        private final List<BankChange> rows = new ArrayList<>();
        private boolean fail;

        void add(BankChange change) {
            rows.add(change);
        }

        @Override
        public List<BankChange> changesSince(Instant since, int limit) {
            if (fail) {
                throw new StorageException("base coupee", null);
            }
            return rows.stream().filter(row -> !row.updatedAt().isBefore(since))
                    .sorted(Comparator.comparing(BankChange::updatedAt).thenComparing(row -> row.bankId().toString()))
                    .limit(limit).toList();
        }
    }

    private static final class FakeBus implements SyncBus {
        private final List<SyncEvent> published = new ArrayList<>();
        private Consumer<SyncEvent> listener;
        private boolean connected = true;

        void emit(SyncEvent event) {
            listener.accept(event);
        }

        @Override
        public boolean publish(SyncEvent event) {
            if (!connected) {
                return false;
            }
            published.add(event);
            return true;
        }

        @Override
        public void subscribe(Consumer<SyncEvent> receiver) {
            this.listener = receiver;
        }

        @Override
        public SyncBusStatus status() {
            return new SyncBusStatus(connected, "fake", "");
        }

        @Override
        public void close() {
        }
    }

    private static final class MutableClock extends Clock {
        private final Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
