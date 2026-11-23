package dev.novadev.novabank.core.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.api.OperationId;
import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.api.valuation.ValuationMode;
import dev.novadev.novabank.core.delivery.PendingDelivery;
import dev.novadev.novabank.core.journal.OperationRecord;
import dev.novadev.novabank.core.money.Money;
import dev.novadev.novabank.core.repository.ApplyOutcome;
import dev.novadev.novabank.core.repository.MutationDecision;
import dev.novadev.novabank.core.repository.StorageException;
import dev.novadev.novabank.core.testing.InMemoryStorageBackend;
import dev.novadev.novabank.core.testing.TestBankIds;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Gel des ecritures pendant une maintenance : attente des ecritures en cours, refus des
 * suivantes, lectures libres, degel.
 */
class FreezableBackendTest {

    private static final ItemKey STONE = ItemKey.minecraft("stone");

    private final InMemoryStorageBackend real = new InMemoryStorageBackend();
    private final FreezableBackend backend = new FreezableBackend(real);
    private final BankId bankId = TestBankIds.random();

    FreezableBackendTest() {
        backend.banks().createBank(bankId, new EntityRef(EntityRef.ISLAND, "ile"), ValuationMode.DERIVED);
    }

    @Test
    void le_gel_attend_l_ecriture_en_cours_puis_refuse_les_suivantes() throws Exception {
        CountDownLatch inside = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        OperationId id = OperationId.random();
        CompletableFuture<ApplyOutcome> running = CompletableFuture.supplyAsync(() ->
                backend.banks().apply(id, bankId, bank -> {
                    inside.countDown();
                    await(release);
                    return MutationDecision.accept(bank.withLine(STONE, bank.line(STONE).plus(1L)),
                            OperationRecord.deposit(id, bankId, STONE, 1L, 1L, Money.ZERO,
                                    Instant.now()));
                }));
        assertThat(inside.await(5, TimeUnit.SECONDS)).isTrue();

        assertThat(backend.freeze(Duration.ofMillis(100))).as("ecriture en cours").isFalse();
        assertThat(backend.isFrozen()).isFalse();
        CompletableFuture<Boolean> frozen = CompletableFuture.supplyAsync(() -> {
            try {
                return backend.freeze(Duration.ofSeconds(5));
            } catch (InterruptedException interrupted) {
                throw new IllegalStateException(interrupted);
            }
        });
        release.countDown();

        assertThat(frozen.get(5, TimeUnit.SECONDS)).isTrue();
        assertThat(running.get(5, TimeUnit.SECONDS)).isInstanceOf(ApplyOutcome.Applied.class);
        assertThatThrownBy(() -> backend.activeBanks().save(UUID.randomUUID(), EntityRef.player(UUID.randomUUID())))
                .isInstanceOf(StorageException.class).hasMessageContaining("maintenance");
        assertThatThrownBy(() -> backend.deliveries().add(new PendingDelivery(OperationId.random(), UUID.randomUUID(),
                STONE, 1L))).isInstanceOf(StorageException.class);
        assertThatThrownBy(() -> backend.banks().createBank(TestBankIds.random(), EntityRef.player(UUID.randomUUID()),
                ValuationMode.DERIVED)).isInstanceOf(StorageException.class);
        assertThat(backend.banks().findBank(bankId)).isPresent();
        assertThat(backend.content()).isSameAs(real.content());
    }

    @Test
    void apres_le_degel_les_ecritures_reprennent() throws Exception {
        assertThat(backend.freeze(Duration.ofSeconds(1))).isTrue();
        backend.unfreeze();

        backend.activeBanks().save(UUID.randomUUID(), EntityRef.player(UUID.randomUUID()));

        assertThat(real.activeBanks().choices()).hasSize(1);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
