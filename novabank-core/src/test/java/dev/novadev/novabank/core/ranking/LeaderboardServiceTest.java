package dev.novadev.novabank.core.ranking;

import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.api.OperationId;
import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.api.valuation.ValuationMode;
import dev.novadev.novabank.core.bank.Bank;
import dev.novadev.novabank.core.journal.OperationRecord;
import dev.novadev.novabank.core.repository.StorageException;
import dev.novadev.novabank.core.storage.StorageContent;
import dev.novadev.novabank.core.sync.BankChange;
import dev.novadev.novabank.core.repository.MutationDecision;
import dev.novadev.novabank.core.testing.InMemoryStorageBackend;
import dev.novadev.novabank.core.testing.ManualScheduler;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LeaderboardServiceTest {

    private static final ItemKey STONE = ItemKey.minecraft("stone");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-28T12:00:00Z"), ZoneOffset.UTC);

    private final InMemoryStorageBackend storage = new InMemoryStorageBackend();
    private final ManualScheduler scheduler = new ManualScheduler();
    private Map<String, Integer> enabled = Map.of("player", 3, "island", 5);
    private boolean contentFails;
    private LeaderboardService service;

    @BeforeEach
    void setUp() {
        Logger quiet = Logger.getLogger("leaderboard-test");
        quiet.setLevel(Level.OFF);
        StorageContent real = storage.content();
        StorageContent content = new StorageContent() {
            @Override
            public boolean isEmpty() {
                return real.isEmpty();
            }

            @Override
            public void exportBanks(Consumer<Bank> sink) {
                if (contentFails) {
                    throw new StorageException("base coupee");
                }
                real.exportBanks(sink);
            }

            @Override
            public void exportOperations(Consumer<OperationRecord> sink) {
                real.exportOperations(sink);
            }

            @Override
            public void exportDeliveries(Consumer<dev.novadev.novabank.core.storage.StoredDelivery> sink) {
                real.exportDeliveries(sink);
            }

            @Override
            public void exportActiveBanks(Consumer<dev.novadev.novabank.core.storage.ActiveBankChoice> sink) {
                real.exportActiveBanks(sink);
            }

            @Override
            public void importBanks(java.util.List<Bank> banks) {
                real.importBanks(banks);
            }

            @Override
            public void importOperations(java.util.List<OperationRecord> operations) {
                real.importOperations(operations);
            }

            @Override
            public void importDeliveries(java.util.List<dev.novadev.novabank.core.storage.StoredDelivery> deliveries) {
                real.importDeliveries(deliveries);
            }

            @Override
            public void importActiveBanks(java.util.List<dev.novadev.novabank.core.storage.ActiveBankChoice> choices) {
                real.importActiveBanks(choices);
            }
        };
        // Valeur d'une banque = nombre de pierres.
        service = new LeaderboardService(storage.banks(), content,
                (bank, category) -> BigDecimal.valueOf(bank.line(STONE).quantity()), () -> enabled, CLOCK, quiet);
        service.start(scheduler, Duration.ofSeconds(60));
        scheduler.runAll();
    }

    private BankId bank(String type, String id, long stones) {
        EntityRef owner = new EntityRef(type, id);
        BankId bankId = BankId.forOwner(owner);
        storage.banks().createBank(bankId, owner, ValuationMode.DERIVED);
        if (stones > 0) {
            deposit(bankId, stones);
        }
        return bankId;
    }

    private void deposit(BankId bankId, long stones) {
        OperationId id = OperationId.random();
        storage.banks().apply(id, bankId, bank -> MutationDecision.accept(
                bank.withLine(STONE, bank.line(STONE).plus(stones)),
                OperationRecord.deposit(id, bankId, STONE, stones, stones, BigDecimal.valueOf(stones), CLOCK.instant())));
    }

    private static BankChange change(BankId bank) {
        return new BankChange(bank, 1, 0, Instant.EPOCH);
    }

    @Test
    void fullRebuildRanksByValueKeepsOnlyPositiveEnabledTypesAndCapsTheSize() {
        BankId a = bank("player", "a", 10);
        BankId b = bank("player", "b", 50);
        bank("player", "c", 0);
        bank("player", "d", 30);
        bank("player", "e", 5);
        bank("faction", "f", 999);
        bank("island", "i", 7);

        service.rebuild();

        assertThat(service.top("player", 10)).extracting(RankedEntry::rank).containsExactly(1, 2, 3);
        assertThat(service.top("player", 10)).extracting(entry -> entry.owner().id()).containsExactly("b", "d", "a");
        assertThat(service.top("player", 2)).hasSize(2);
        assertThat(service.top("faction", 10)).as("type non classe").isEmpty();
        assertThat(service.top("island", 10)).singleElement().satisfies(entry -> assertThat(entry.value())
                .isEqualByComparingTo("7"));
        assertThat(service.rankOf("player", b)).hasValueSatisfying(entry -> assertThat(entry.rank()).isEqualTo(1));
        assertThat(service.rankOf("player", a)).hasValueSatisfying(entry -> assertThat(entry.rank()).isEqualTo(3));
        assertThat(service.stats().entries()).containsEntry("player", 4).containsEntry("island", 1);
        assertThat(service.stats().lastRebuild()).contains(CLOCK.instant());
    }

    @Test
    void rankOfIsNotLimitedByTheDisplayedSize() {
        for (int i = 1; i <= 6; i++) {
            bank("player", "p" + i, i * 10L);
        }
        BankId last = bank("player", "tiny", 1);
        service.rebuild();

        assertThat(service.top("player", 100)).hasSize(3);
        assertThat(service.rankOf("player", last)).hasValueSatisfying(entry -> assertThat(entry.rank()).isEqualTo(7));
    }

    @Test
    void equalValuesAreOrderedByOwnerSoTheRankingIsDeterministic() {
        bank("player", "zed", 10);
        bank("player", "amy", 10);
        service.rebuild();

        assertThat(service.top("player", 10)).extracting(entry -> entry.owner().id()).containsExactly("amy", "zed");
    }

    @Test
    void aLocalOrRemoteChangeRefreshesOnlyThatBankAndRepeatedChangesAreMerged() {
        BankId a = bank("player", "a", 10);
        BankId b = bank("player", "b", 20);
        service.rebuild();

        deposit(a, 100);
        service.localChange(change(a));
        service.onRemoteChange(change(a));
        service.onRemoteChange(change(a));
        assertThat(scheduler.runAll()).as("une seule tache pour la banque a").isEqualTo(1);

        assertThat(service.top("player", 10)).extracting(entry -> entry.owner().id()).containsExactly("a", "b");
        assertThat(service.rankOf("player", b)).hasValueSatisfying(entry -> assertThat(entry.rank()).isEqualTo(2));
        assertThat(service.stats().refreshes()).isEqualTo(1);
    }

    @Test
    void aBankThatFallsToZeroOrAppearsLaterIsHandled() {
        BankId a = bank("player", "a", 10);
        service.rebuild();
        BankId fresh = bank("player", "fresh", 40);
        service.onRemoteChange(change(fresh));
        scheduler.runAll();
        assertThat(service.top("player", 10)).extracting(entry -> entry.owner().id()).containsExactly("fresh", "a");

        OperationId id = OperationId.random();
        storage.banks().apply(id, a, bank -> MutationDecision.accept(bank.withLine(STONE, bank.line(STONE).minus(10)),
                OperationRecord.deposit(id, a, STONE, 0, 0, BigDecimal.ZERO, CLOCK.instant())));
        service.localChange(change(a));
        scheduler.runAll();

        assertThat(service.top("player", 10)).extracting(entry -> entry.owner().id()).containsExactly("fresh");
        assertThat(service.rankOf("player", a)).isEmpty();
    }

    @Test
    void aFailedRebuildKeepsTheOldRankingAndReportsTheError() {
        bank("player", "a", 10);
        service.rebuild();

        contentFails = true;
        service.rebuild();

        assertThat(service.top("player", 10)).hasSize(1);
        assertThat(service.stats().lastError()).isPresent();

        contentFails = false;
        service.rebuild();
        assertThat(service.stats().lastError()).isEmpty();
    }

    @Test
    void theConfigurationIsReadAgainAtEveryUse() {
        bank("player", "a", 10);
        bank("player", "b", 20);
        service.rebuild();
        assertThat(service.top("player", 10)).hasSize(2);

        enabled = Map.of("player", 1);
        assertThat(service.top("player", 10)).hasSize(1);

        enabled = Map.of();
        service.requestRebuild();
        scheduler.runAll();
        assertThat(service.top("player", 10)).isEmpty();
    }

    @Test
    void afterCloseNothingIsScheduled() {
        BankId a = bank("player", "a", 10);
        service.close();

        service.localChange(change(a));
        service.requestRebuild();

        assertThat(scheduler.runAll()).isZero();
    }
}
