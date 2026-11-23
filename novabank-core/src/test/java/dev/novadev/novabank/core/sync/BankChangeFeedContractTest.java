package dev.novadev.novabank.core.sync;

import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.api.OperationId;
import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.api.valuation.ValuationMode;
import dev.novadev.novabank.core.journal.OperationRecord;
import dev.novadev.novabank.core.repository.ApplyOutcome;
import dev.novadev.novabank.core.repository.BankRepository;
import dev.novadev.novabank.core.repository.MutationDecision;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Contrat du {@link BankChangeFeed} : chaque mutation acceptee est retrouvee, fondue par banque,
 * dans l'ordre des dates, et rien n'apparait pour un refus. A etendre pour chaque backend partageable.
 */
public abstract class BankChangeFeedContractTest {

    private static final ItemKey STONE = ItemKey.minecraft("stone");

    private BankRepository repository;
    private BankChangeFeed feed;

    /**
     * @return un depot neuf et son flux de changements (meme stockage)
     */
    protected abstract FeedFixture newFixture();

    /**
     * Depot et flux d'un meme stockage.
     *
     * @param repository banques
     * @param feed       flux de changements
     */
    public record FeedFixture(BankRepository repository, BankChangeFeed feed) {
    }

    @BeforeEach
    void open() {
        FeedFixture fixture = newFixture();
        repository = fixture.repository();
        feed = fixture.feed();
    }

    @Test
    void anAcceptedMutationAppearsWithItsVersion() {
        BankId bank = newBank();

        deposit(bank);

        assertThat(feed.changesSince(Instant.EPOCH, 10)).singleElement().satisfies(change -> {
            assertThat(change.bankId()).isEqualTo(bank);
            assertThat(change.version()).isEqualTo(1L);
            assertThat(change.resetEpoch()).isZero();
        });
    }

    @Test
    void successiveMutationsOfOneBankAreCoalescedToTheLatestVersion() {
        BankId bank = newBank();

        deposit(bank);
        deposit(bank);
        deposit(bank);

        assertThat(feed.changesSince(Instant.EPOCH, 10)).singleElement()
                .satisfies(change -> assertThat(change.version()).isEqualTo(3L));
    }

    @Test
    void aRefusedOrReplayedOperationDoesNotChangeTheVersion() {
        BankId bank = newBank();
        OperationId id = deposit(bank);

        ApplyOutcome replay = repository.apply(id, bank, current -> MutationDecision.accept(current, null));

        assertThat(replay).isInstanceOf(ApplyOutcome.Duplicate.class);
        assertThat(feed.changesSince(Instant.EPOCH, 10)).singleElement()
                .satisfies(change -> assertThat(change.version()).isEqualTo(1L));
    }

    @Test
    void sinceIsInclusiveOrderedByDateAndLimited() throws InterruptedException {
        BankId first = newBank();
        deposit(first);
        Thread.sleep(20);
        BankId second = newBank();
        deposit(second);
        Instant secondDate = feed.changesSince(Instant.EPOCH, 10).stream()
                .filter(change -> change.bankId().equals(second)).findFirst().orElseThrow().updatedAt();
        Thread.sleep(20);
        BankId third = newBank();
        deposit(third);

        List<BankChange> all = feed.changesSince(Instant.EPOCH, 10);
        List<BankChange> fromSecond = feed.changesSince(secondDate, 10);

        assertThat(all).extracting(BankChange::bankId).containsExactly(first, second, third);
        assertThat(fromSecond).extracting(BankChange::bankId).containsExactly(second, third);
        assertThat(feed.changesSince(Instant.EPOCH, 2)).extracting(BankChange::bankId).containsExactly(first, second);
        assertThat(feed.changesSince(Instant.now().plusSeconds(3600), 10)).isEmpty();
    }

    private BankId newBank() {
        BankId id = new BankId(UUID.randomUUID());
        repository.createBank(id, new EntityRef(EntityRef.PLAYER, id.toString()), ValuationMode.DERIVED);
        return id;
    }

    private OperationId deposit(BankId bank) {
        OperationId id = OperationId.random();
        ApplyOutcome outcome = repository.apply(id, bank, current -> MutationDecision.accept(
                current.withLine(STONE, current.line(STONE).plus(1L)),
                OperationRecord.deposit(id, bank, STONE, 1L, 1L, BigDecimal.ONE, Instant.now())));
        assertThat(outcome).isInstanceOf(ApplyOutcome.Applied.class);
        return id;
    }
}
