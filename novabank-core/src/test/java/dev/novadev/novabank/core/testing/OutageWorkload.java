package dev.novadev.novabank.core.testing;

import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.api.OperationId;
import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.api.operation.OperationResult;
import dev.novadev.novabank.api.operation.OperationStatus;
import dev.novadev.novabank.core.ledger.BankLedger;
import dev.novadev.novabank.core.repository.StorageException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Charge de travail des tests de panne reelle d'un serveur de base, commune a tous les backends :
 * des threads deposent puis retirent en boucle sur une banque pendant que le test tue puis
 * relance le serveur.
 *
 * <p>Chaque depot annonce reussi est memorise ; une panne doit se traduire par une
 * {@link StorageException} (toute autre exception est consignee comme inattendue).
 */
public final class OutageWorkload {

    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicBoolean restarted = new AtomicBoolean();
    private final AtomicInteger storageFailures = new AtomicInteger();
    private final AtomicInteger successesAfterRestart = new AtomicInteger();
    private final AtomicLong successes = new AtomicLong();
    private final List<OperationId> confirmedDeposits = Collections.synchronizedList(new ArrayList<>());
    private final List<Throwable> unexpected = Collections.synchronizedList(new ArrayList<>());
    private final List<String> failureMessages = Collections.synchronizedList(new ArrayList<>());
    private final ExecutorService executor;
    private final List<Future<?>> tasks = new ArrayList<>();

    private OutageWorkload(BankLedger ledger, BankId bankId, ItemKey item, int workers) {
        executor = Executors.newFixedThreadPool(workers);
        for (int worker = 0; worker < workers; worker++) {
            Random random = new Random(worker);
            tasks.add(executor.submit(() -> loop(ledger, bankId, item, random)));
        }
    }

    /**
     * @param ledger  service du coeur branche sur le backend teste
     * @param bankId  banque existante
     * @param item    item depose et retire
     * @param workers nombre de threads
     * @return la charge demarree
     */
    public static OutageWorkload start(BankLedger ledger, BankId bankId, ItemKey item, int workers) {
        return new OutageWorkload(ledger, bankId, item, workers);
    }

    /**
     * @return operations (depot puis retrait) reussies depuis le demarrage
     */
    public long successes() {
        return successes.get();
    }

    /**
     * @return pannes propres ({@link StorageException}) constatees
     */
    public int storageFailures() {
        return storageFailures.get();
    }

    /**
     * Signale que le serveur a ete relance, puis attend que des operations reussissent a nouveau.
     *
     * @param count   nombre d'operations reussies attendues apres la relance
     * @param timeout delai maximal
     * @return le nombre d'operations reussies apres la relance
     */
    public int awaitRecovery(int count, Duration timeout) throws InterruptedException {
        restarted.set(true);
        long deadline = System.nanoTime() + timeout.toNanos();
        while (successesAfterRestart.get() < count && System.nanoTime() < deadline) {
            TimeUnit.MILLISECONDS.sleep(100);
        }
        return successesAfterRestart.get();
    }

    /**
     * Arrete les threads et remonte leurs erreurs d'assertion.
     */
    public void stop() throws Exception {
        running.set(false);
        executor.shutdown();
        assertThat(executor.awaitTermination(60, TimeUnit.SECONDS)).isTrue();
        for (Future<?> task : tasks) {
            task.get();
        }
    }

    /**
     * @return les depots annonces reussis
     */
    public List<OperationId> confirmedDeposits() {
        return List.copyOf(confirmedDeposits);
    }

    /**
     * @return les exceptions autres que {@link StorageException}
     */
    public List<Throwable> unexpected() {
        return List.copyOf(unexpected);
    }

    /**
     * @return les messages des pannes propres
     */
    public List<String> failureMessages() {
        return List.copyOf(failureMessages);
    }

    private void loop(BankLedger ledger, BankId bankId, ItemKey item, Random random) {
        while (running.get()) {
            try {
                OperationId deposit = OperationId.random();
                OperationResult result = ledger.deposit(deposit, bankId, item, 1L + random.nextInt(9));
                assertThat(result.status()).isEqualTo(OperationStatus.SUCCESS);
                confirmedDeposits.add(deposit);
                OperationId withdrawal = OperationId.random();
                if (ledger.reserveWithdrawal(withdrawal, bankId, item, 1L + random.nextInt(5),
                        UUID.randomUUID()).isSuccess()) {
                    ledger.confirmWithdrawal(withdrawal, random.nextInt(2));
                }
                successes.incrementAndGet();
                if (restarted.get()) {
                    successesAfterRestart.incrementAndGet();
                }
            } catch (StorageException failure) {
                storageFailures.incrementAndGet();
                failureMessages.add(String.valueOf(failure.getMessage()));
                pause();
            } catch (Throwable failure) {
                unexpected.add(failure);
                pause();
            }
        }
    }

    private static void pause() {
        try {
            TimeUnit.MILLISECONDS.sleep(100);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
