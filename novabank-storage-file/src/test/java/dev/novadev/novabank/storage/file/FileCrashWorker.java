package dev.novadev.novabank.storage.file;

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
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * JVM fille du test d'arret brutal : ouvre le stockage (format, seuil de compaction faible),
 * reconcilie les retraits restes reserves, annonce {@code READY}, puis enchaine des operations sur
 * 4 threads jusqu'a etre tuee. Chaque operation CONFIRMEE (resultat rendu, donc journal
 * synchronise) est annoncee par une ligne {@code OK <operation>} : le parent verifie qu'aucune
 * n'est perdue. Toute anomalie termine le processus avec le code 3.
 */
public final class FileCrashWorker {

    static final BankId ACCUMULATED_BANK = new BankId(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    static final BankId DERIVED_BANK = new BankId(UUID.fromString("00000000-0000-0000-0000-0000000000d1"));
    private static final ItemKey[] ITEMS = {ItemKey.minecraft("stone"), ItemKey.minecraft("gold_block")};
    private static final BankId[] BANKS = {ACCUMULATED_BANK, DERIVED_BANK};
    private static final int THREADS = 4;
    private static final long COMPACTION_BYTES = 64 * 1024;
    private static final PrintStream OUT = new PrintStream(System.out, false, StandardCharsets.UTF_8);

    private FileCrashWorker() {
    }

    /**
     * @param args dossier, format, graine
     * @throws InterruptedException jamais en pratique : le processus est tue
     */
    public static void main(String[] args) throws InterruptedException {
        FileStorage storage = FileStorage.open(new FileStorageOptions(Path.of(args[0]), FileFormat.valueOf(args[1]),
                COMPACTION_BYTES, Duration.ofHours(1), Clock.systemUTC(), Logger.getLogger("crash-worker")));
        long seed = Long.parseLong(args[2]);
        MutablePriceCatalog catalog = new MutablePriceCatalog().price(ITEMS[0], "1.25").price(ITEMS[1], "12.3456");
        BankLedger ledger = new BankLedger(storage.banks(), catalog, storage.deliveries(),
                UndeliveredPolicy.values()[Math.floorMod(seed, 2)], Clock.systemUTC());
        ledger.openBank(ACCUMULATED_BANK, new EntityRef("island", "crash-a"), ValuationMode.ACCUMULATED);
        ledger.openBank(DERIVED_BANK, new EntityRef("faction", "crash-d"), ValuationMode.DERIVED);
        int reconciled = 0;
        for (OperationRecord orphan : storage.banks().reservedWithdrawals()) {
            require(ledger.cancelWithdrawal(orphan.operationId()), OperationStatus.SUCCESS);
            reconciled++;
        }
        synchronized (OUT) {
            OUT.println("READY " + reconciled);
            OUT.flush();
        }
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
            while (true) {
                BankId bank = BANKS[random.nextInt(BANKS.length)];
                ItemKey item = ITEMS[random.nextInt(ITEMS.length)];
                int roll = random.nextInt(100);
                if (roll < 45) {
                    OperationId id = OperationId.random();
                    confirmed(require(ledger.deposit(id, bank, item, 1L + random.nextInt(64)), OperationStatus.SUCCESS));
                } else if (roll < 97) {
                    withdraw(ledger, random, bank, item);
                } else {
                    confirmed(require(ledger.reset(OperationId.random(), bank), OperationStatus.SUCCESS));
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
        confirmed(require(reserved, OperationStatus.SUCCESS));
        int fate = random.nextInt(10);
        if (fate < 7) {
            require(ledger.confirmWithdrawal(id, random.nextInt((int) quantity + 1)), OperationStatus.SUCCESS);
        } else if (fate < 9) {
            require(ledger.cancelWithdrawal(id), OperationStatus.SUCCESS);
        }
        // Sinon : retrait laisse reserve, comme lors d'un arret entre reservation et livraison.
    }

    private static OperationResult require(OperationResult result, OperationStatus expected) {
        if (result.status() != expected) {
            throw new IllegalStateException("Statut " + result.status() + " au lieu de " + expected + " pour "
                    + result.operationId());
        }
        return result;
    }

    private static void confirmed(OperationResult result) {
        synchronized (OUT) {
            OUT.println("OK " + result.operationId());
            OUT.flush();
        }
    }
}
