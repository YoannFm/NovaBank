package dev.novadev.novabank.storage.sql;

import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.api.OperationId;
import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.api.operation.OperationResult;
import dev.novadev.novabank.api.operation.OperationStatus;
import dev.novadev.novabank.api.valuation.ValuationMode;
import dev.novadev.novabank.core.delivery.UndeliveredPolicy;
import dev.novadev.novabank.core.journal.OperationRecord;
import dev.novadev.novabank.core.ledger.BankLedger;
import dev.novadev.novabank.core.testing.MutablePriceCatalog;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Random;
import java.util.UUID;

/**
 * JVM fille du test d'arret brutal : reconcilie les retraits restes reserves, annonce
 * {@code READY}, puis enchaine des operations sur 4 threads jusqu'a etre tuee.
 *
 * <p>Arguments : fichier de base, graine, politique de non-livraison. Toute anomalie
 * (exception, statut inattendu) termine le processus avec le code 3 : le parent le voit
 * mort avant de le tuer.
 */
public final class CrashWorker {

    static final BankId ACCUMULATED_BANK = new BankId(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    static final BankId DERIVED_BANK = new BankId(UUID.fromString("00000000-0000-0000-0000-0000000000d1"));
    private static final ItemKey[] ITEMS = {ItemKey.minecraft("stone"), ItemKey.minecraft("gold_block")};
    private static final BankId[] BANKS = {ACCUMULATED_BANK, DERIVED_BANK};
    private static final int THREADS = 4;

    private CrashWorker() {
    }

    /**
     * @param args fichier de base, graine, politique ({@link UndeliveredPolicy})
     * @throws InterruptedException jamais en pratique : le processus est tue
     */
    public static void main(String[] args) throws InterruptedException {
        SqlStorage storage = SqliteTestSupport.open(Path.of(args[0]));
        long seed = Long.parseLong(args[1]);
        MutablePriceCatalog catalog = new MutablePriceCatalog()
                .price(ITEMS[0], "1.25")
                .price(ITEMS[1], "12.3456");
        BankLedger ledger = new BankLedger(storage.banks(), catalog, storage.deliveries(),
                UndeliveredPolicy.valueOf(args[2]), Clock.systemUTC());
        ledger.openBank(ACCUMULATED_BANK, new EntityRef("island", "crash-a"), ValuationMode.ACCUMULATED);
        ledger.openBank(DERIVED_BANK, new EntityRef("faction", "crash-d"), ValuationMode.DERIVED);
        int reconciled = 0;
        for (OperationRecord orphan : storage.banks().reservedWithdrawals()) {
            require(ledger.cancelWithdrawal(orphan.operationId()), OperationStatus.SUCCESS);
            reconciled++;
        }
        System.out.println("READY " + reconciled);
        System.out.flush();
        Thread[] workers = new Thread[THREADS];
        for (int i = 0; i < THREADS; i++) {
            Random random = new Random(seed * 31 + i);
            workers[i] = new Thread(() -> work(ledger, random), "crash-worker-" + i);
            workers[i].start();
        }
        for (Thread worker : workers) {
            worker.join();
        }
    }

    private static void work(BankLedger ledger, Random random) {
        try {
            OperationId lastDeposit = null;
            while (true) {
                BankId bank = BANKS[random.nextInt(BANKS.length)];
                ItemKey item = ITEMS[random.nextInt(ITEMS.length)];
                int roll = random.nextInt(100);
                if (roll < 40) {
                    lastDeposit = OperationId.random();
                    require(ledger.deposit(lastDeposit, bank, item, 1L + random.nextInt(64)), OperationStatus.SUCCESS);
                } else if (roll < 50 && lastDeposit != null) {
                    require(ledger.deposit(lastDeposit, bank, item, 1L), OperationStatus.DUPLICATE_OPERATION);
                } else if (roll < 97) {
                    withdraw(ledger, random, bank, item);
                } else {
                    require(ledger.reset(OperationId.random(), bank), OperationStatus.SUCCESS);
                }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            System.exit(3);
        }
    }

    private static void withdraw(BankLedger ledger, Random random, BankId bank, ItemKey item) {
        OperationId id = OperationId.random();
        long quantity = 1L + random.nextInt(32);
        OperationResult reserved = ledger.reserveWithdrawal(id, bank, item, quantity, UUID.randomUUID());
        if (reserved.status() == OperationStatus.INSUFFICIENT_STOCK) {
            return;
        }
        require(reserved, OperationStatus.SUCCESS);
        int fate = random.nextInt(10);
        if (fate < 7) {
            require(ledger.confirmWithdrawal(id, random.nextInt((int) quantity + 1)), OperationStatus.SUCCESS);
        } else if (fate < 9) {
            require(ledger.cancelWithdrawal(id), OperationStatus.SUCCESS);
        }
        // Sinon : retrait laisse reserve, comme lors d'un arret entre reservation et livraison.
    }

    private static void require(OperationResult result, OperationStatus expected) {
        if (result.status() != expected) {
            throw new IllegalStateException("Statut " + result.status() + " au lieu de " + expected + " pour "
                    + result.operationId());
        }
    }
}
