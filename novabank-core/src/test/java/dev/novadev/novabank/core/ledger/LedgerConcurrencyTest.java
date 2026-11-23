package dev.novadev.novabank.core.ledger;

import static dev.novadev.novabank.core.ledger.LedgerFixture.STONE;
import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.api.OperationId;
import dev.novadev.novabank.api.operation.OperationResult;
import dev.novadev.novabank.api.operation.OperationStatus;
import dev.novadev.novabank.api.valuation.ValuationMode;
import dev.novadev.novabank.core.delivery.UndeliveredPolicy;
import dev.novadev.novabank.core.testing.ConcurrentRunner;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Concurrence reelle sur une meme banque : 16 threads liberes ensemble.
 */
class LedgerConcurrencyTest {

    private static final int THREADS = 16;
    private static final int OPERATIONS_PER_THREAD = 250;
    private static final long INITIAL_STOCK = 100L;

    @ParameterizedTest
    @EnumSource(ValuationMode.class)
    void depots_et_retraits_concurrents_gardent_un_stock_exact(ValuationMode mode) throws Exception {
        LedgerFixture f = LedgerFixture.of(mode);
        f.deposit(STONE, INITIAL_STOCK);
        AtomicLong deposited = new AtomicLong();
        AtomicLong withdrawn = new AtomicLong();
        AtomicLong acceptedOperations = new AtomicLong();
        AtomicLong refusedWithdrawals = new AtomicLong();

        ConcurrentRunner.run(THREADS, thread -> {
            Random random = new Random(thread);
            for (int i = 0; i < OPERATIONS_PER_THREAD; i++) {
                if (random.nextInt(10) < 4) {
                    long quantity = 1L + random.nextInt(5);
                    assertThat(f.deposit(STONE, quantity).status()).isEqualTo(OperationStatus.SUCCESS);
                    deposited.addAndGet(quantity);
                    acceptedOperations.incrementAndGet();
                } else {
                    long quantity = 1L + random.nextInt(8);
                    OperationId id = OperationId.random();
                    OperationResult reserved = f.reserve(id, STONE, quantity);
                    if (reserved.isSuccess()) {
                        assertThat(f.ledger.confirmWithdrawal(id, quantity).isSuccess()).isTrue();
                        withdrawn.addAndGet(quantity);
                        acceptedOperations.incrementAndGet();
                    } else {
                        assertThat(reserved.status()).isEqualTo(OperationStatus.INSUFFICIENT_STOCK);
                        refusedWithdrawals.incrementAndGet();
                    }
                }
            }
        });

        long expected = INITIAL_STOCK + deposited.get() - withdrawn.get();
        assertThat(expected).isNotNegative();
        assertThat(f.line(STONE).quantity()).isEqualTo(expected);
        assertThat(f.value()).isEqualByComparingTo(String.valueOf(expected));
        assertThat(refusedWithdrawals.get()).as("le stock doit s'epuiser pour tester les refus").isPositive();
        assertThat(f.repository.journal()).hasSize((int) (1L + acceptedOperations.get()));
    }

    @ParameterizedTest
    @EnumSource(ValuationMode.class)
    void le_rejeu_concurrent_de_chaque_etape_n_a_qu_un_effet(ValuationMode mode) throws Exception {
        LedgerFixture f = LedgerFixture.of(mode, UndeliveredPolicy.PENDING_DELIVERY);
        OperationId deposit = OperationId.random();
        OperationId reserve = OperationId.random();
        OperationId cancelled = OperationId.random();

        assertSingleEffect(() -> f.ledger.deposit(deposit, f.bankId, STONE, 50));
        assertSingleEffect(() -> f.reserve(reserve, STONE, 10));
        assertSingleEffect(() -> f.ledger.confirmWithdrawal(reserve, 4));
        f.reserve(cancelled, STONE, 5);
        assertSingleEffect(() -> f.ledger.cancelWithdrawal(cancelled));

        assertThat(f.line(STONE).quantity()).isEqualTo(40L);
        assertThat(f.value()).isEqualByComparingTo("40");
        assertThat(f.deliveries.find(reserve)).hasValueSatisfying(d -> assertThat(d.quantity()).isEqualTo(6L));
        assertThat(f.deliveries.size()).isEqualTo(1);
        assertThat(f.repository.journal()).hasSize(3);
    }

    private static void assertSingleEffect(Supplier<OperationResult> step) throws Exception {
        Map<OperationStatus, AtomicLong> statuses = new ConcurrentHashMap<>();
        ConcurrentRunner.run(THREADS, thread ->
                statuses.computeIfAbsent(step.get().status(), status -> new AtomicLong()).incrementAndGet());
        assertThat(statuses.keySet())
                .containsExactlyInAnyOrderElementsOf(List.of(OperationStatus.SUCCESS, OperationStatus.DUPLICATE_OPERATION));
        assertThat(statuses.get(OperationStatus.SUCCESS)).hasValue(1L);
        assertThat(statuses.get(OperationStatus.DUPLICATE_OPERATION)).hasValue(THREADS - 1L);
    }
}
