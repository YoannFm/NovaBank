package dev.novadev.novabank.core.testing;

import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.api.OperationId;
import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.api.operation.OperationResult;
import dev.novadev.novabank.api.operation.OperationStatus;
import dev.novadev.novabank.api.valuation.ValuationMode;
import dev.novadev.novabank.core.delivery.UndeliveredPolicy;
import dev.novadev.novabank.core.ledger.BankLedger;
import dev.novadev.novabank.core.storage.StorageBackend;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Scenario de concurrence reelle commun a tous les moteurs : depots, retraits en deux temps et
 * rejeux d'un meme identifiant depuis plusieurs threads repartis sur plusieurs stockages (un
 * stockage = un pool = un serveur Minecraft), via le service metier du coeur, sur une banque.
 *
 * <p>Verifie ensuite, pour la banque du scenario : stock final exact et jamais negatif, chaque
 * depot partage utilise applique une seule fois, aucun retrait reste reserve, aucune operation
 * refusee pour conflit. La coherence journal / banques de toute la base se verifie par chaque
 * backend une fois tous les scenarios termines. Partage par les tests de tous les backends.
 */
public final class LedgerConcurrencyScenario {

    private static final ItemKey STONE = ItemKey.minecraft("stone");
    private static final int SHARED_DEPOSITS = 40;
    private static final long SHARED_QUANTITY = 5L;

    private LedgerConcurrencyScenario() {
    }

    /**
     * @param storages backends ouverts sur la meme base (un par serveur simule), threads repartis en tourniquet
     * @param threads  nombre de threads
     * @param rounds   tours par thread (un depot partage, un depot + rejeu, un retrait + rejeu)
     */
    public static void run(List<? extends StorageBackend> storages, int threads, int rounds) throws Exception {
        BankId bankId = TestBankIds.random();
        MutablePriceCatalog catalog = new MutablePriceCatalog().price(STONE, "1.2345");
        List<BankLedger> ledgers = new ArrayList<>();
        for (StorageBackend storage : storages) {
            ledgers.add(new BankLedger(storage.banks(), catalog, storage.deliveries(),
                    UndeliveredPolicy.RETURN_TO_BANK, Clock.systemUTC()));
        }
        ledgers.get(0).openBank(bankId, EntityRef.player(UUID.randomUUID()), ValuationMode.ACCUMULATED);
        List<OperationId> shared = new ArrayList<>();
        for (int i = 0; i < SHARED_DEPOSITS; i++) {
            shared.add(OperationId.random());
        }
        Map<OperationId, AtomicInteger> sharedSuccesses = new ConcurrentHashMap<>();
        AtomicLong expectedStock = new AtomicLong();

        ConcurrentRunner.run(threads, thread -> {
            BankLedger ledger = ledgers.get(thread % ledgers.size());
            Random random = new Random(thread);
            for (int round = 0; round < rounds; round++) {
                OperationId sharedId = shared.get((thread + round) % SHARED_DEPOSITS);
                OperationResult sharedDeposit = ledger.deposit(sharedId, bankId, STONE, SHARED_QUANTITY);
                if (sharedDeposit.isSuccess()) {
                    sharedSuccesses.computeIfAbsent(sharedId, id -> new AtomicInteger()).incrementAndGet();
                    expectedStock.addAndGet(SHARED_QUANTITY);
                } else {
                    assertThat(sharedDeposit.status()).isEqualTo(OperationStatus.DUPLICATE_OPERATION);
                }

                OperationId deposit = OperationId.random();
                long quantity = 1L + random.nextInt(20);
                assertThat(ledger.deposit(deposit, bankId, STONE, quantity).status()).isEqualTo(OperationStatus.SUCCESS);
                expectedStock.addAndGet(quantity);
                assertThat(ledger.deposit(deposit, bankId, STONE, quantity).status())
                        .isEqualTo(OperationStatus.DUPLICATE_OPERATION);

                OperationId withdrawal = OperationId.random();
                long wanted = 1L + random.nextInt(30);
                OperationResult reserved = ledger.reserveWithdrawal(withdrawal, bankId, STONE, wanted, UUID.randomUUID());
                if (!reserved.isSuccess()) {
                    assertThat(reserved.status()).isEqualTo(OperationStatus.INSUFFICIENT_STOCK);
                    continue;
                }
                expectedStock.addAndGet(-wanted);
                long delivered = random.nextInt((int) wanted + 1);
                assertThat(ledger.confirmWithdrawal(withdrawal, delivered).status()).isEqualTo(OperationStatus.SUCCESS);
                expectedStock.addAndGet(wanted - delivered);
                assertThat(ledger.confirmWithdrawal(withdrawal, delivered).status())
                        .isEqualTo(OperationStatus.DUPLICATE_OPERATION);
            }
        });

        long stock = storages.get(0).banks().findBank(bankId).orElseThrow().line(STONE).quantity();
        assertThat(stock).isEqualTo(expectedStock.get()).isNotNegative();
        // Identifiants partages effectivement utilises : indices (thread + tour) modulo SHARED_DEPOSITS.
        assertThat(sharedSuccesses).hasSize(Math.min(SHARED_DEPOSITS, threads + rounds - 1));
        assertThat(sharedSuccesses.values()).allMatch(count -> count.get() == 1);
        assertThat(storages.get(0).banks().reservedWithdrawals())
                .noneMatch(record -> record.bankId().equals(bankId));
    }
}
