package dev.novadev.novabank.core.storage;

import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.api.OperationId;
import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.valuation.ValuationMode;
import dev.novadev.novabank.core.bank.Bank;
import dev.novadev.novabank.core.delivery.PendingDelivery;
import dev.novadev.novabank.core.delivery.PendingDeliveryStore;
import dev.novadev.novabank.core.journal.OperationRecord;
import dev.novadev.novabank.core.repository.ApplyOutcome;
import dev.novadev.novabank.core.repository.BankMutation;
import dev.novadev.novabank.core.repository.BankRepository;
import dev.novadev.novabank.core.repository.RecordTransition;
import dev.novadev.novabank.core.repository.StorageException;
import dev.novadev.novabank.core.selection.ActiveBankStore;
import dev.novadev.novabank.core.sync.BankChangeFeed;
import dev.novadev.novabank.core.text.ConsoleText;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/**
 * Backend dont les ecritures peuvent etre gelees (maintenance pendant une migration) : les ports
 * renvoyes delegent au backend reel, chaque ecriture passant par une barriere.
 *
 * <p>{@link #freeze(Duration)} attend la fin des ecritures en cours puis refuse toutes les
 * suivantes ({@link StorageException}) jusqu'a {@link #unfreeze()} ; les lectures ne sont jamais
 * bloquees. Le contenu ({@link #content()}) est celui du backend reel, pour la copie. La fermeture
 * ferme le backend reel.
 */
public final class FreezableBackend implements StorageBackend {

    private final StorageBackend delegate;
    private final ReentrantReadWriteLock barrier = new ReentrantReadWriteLock();
    private volatile boolean frozen;
    private final BankRepository banks;
    private final PendingDeliveryStore deliveries;
    private final ActiveBankStore activeBanks;

    /**
     * @param delegate backend reel
     */
    public FreezableBackend(StorageBackend delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.banks = new GuardedBanks(delegate.banks());
        this.deliveries = new GuardedDeliveries(delegate.deliveries());
        this.activeBanks = new GuardedActiveBanks(delegate.activeBanks());
    }

    /**
     * Gele les ecritures : attend la fin de celles en cours, puis refuse les suivantes.
     *
     * @param timeout attente maximale des ecritures en cours
     * @return {@code true} si les ecritures sont gelees, {@code false} si le delai est depasse
     *         (rien n'est alors gele)
     * @throws InterruptedException si l'attente est interrompue
     */
    public boolean freeze(Duration timeout) throws InterruptedException {
        if (!barrier.writeLock().tryLock(timeout.toNanos(), TimeUnit.NANOSECONDS)) {
            return false;
        }
        try {
            frozen = true;
            return true;
        } finally {
            barrier.writeLock().unlock();
        }
    }

    /**
     * Autorise de nouveau les ecritures.
     */
    public void unfreeze() {
        frozen = false;
    }

    /**
     * @return {@code true} pendant le gel
     */
    public boolean isFrozen() {
        return frozen;
    }

    @Override
    public BankRepository banks() {
        return banks;
    }

    @Override
    public PendingDeliveryStore deliveries() {
        return deliveries;
    }

    @Override
    public ActiveBankStore activeBanks() {
        return activeBanks;
    }

    @Override
    public StorageContent content() {
        return delegate.content();
    }

    @Override
    public Optional<BankChangeFeed> changeFeed() {
        return delegate.changeFeed();
    }

    @Override
    public int schemaVersion() {
        return delegate.schemaVersion();
    }

    @Override
    public int checkSchemaVersion() {
        return delegate.checkSchemaVersion();
    }

    @Override
    public void close() {
        delegate.close();
    }

    private <T> T write(Supplier<T> work) {
        barrier.readLock().lock();
        try {
            if (frozen) {
                throw new StorageException(
                        ConsoleText.tr("Ecriture refusee : NovaBank est en maintenance (migration du stockage)"));
            }
            return work.get();
        } finally {
            barrier.readLock().unlock();
        }
    }

    private void write(Runnable work) {
        write(() -> {
            work.run();
            return null;
        });
    }

    private final class GuardedBanks implements BankRepository {

        private final BankRepository target;

        GuardedBanks(BankRepository target) {
            this.target = target;
        }

        @Override
        public Optional<Bank> findBank(BankId bankId) {
            return target.findBank(bankId);
        }

        @Override
        public Bank createBank(BankId bankId, EntityRef owner, ValuationMode valuationMode) {
            return write(() -> target.createBank(bankId, owner, valuationMode));
        }

        @Override
        public Optional<OperationRecord> findOperation(OperationId operationId) {
            return target.findOperation(operationId);
        }

        @Override
        public ApplyOutcome apply(OperationId operationId, BankId bankId, BankMutation mutation) {
            return write(() -> target.apply(operationId, bankId, mutation));
        }

        @Override
        public ApplyOutcome settle(OperationId operationId, RecordTransition transition) {
            return write(() -> target.settle(operationId, transition));
        }

        @Override
        public List<OperationRecord> reservedWithdrawals() {
            return target.reservedWithdrawals();
        }

        @Override
        public List<OperationRecord> history(BankId bankId, int offset, int limit) {
            return target.history(bankId, offset, limit);
        }
    }

    private final class GuardedDeliveries implements PendingDeliveryStore {

        private final PendingDeliveryStore target;

        GuardedDeliveries(PendingDeliveryStore target) {
            this.target = target;
        }

        @Override
        public void add(PendingDelivery delivery) {
            write(() -> target.add(delivery));
        }

        @Override
        public Optional<PendingDelivery> find(OperationId operationId) {
            return target.find(operationId);
        }

        @Override
        public List<PendingDelivery> pendingFor(UUID playerId) {
            return target.pendingFor(playerId);
        }

        @Override
        public boolean markDelivered(OperationId operationId, long quantity) {
            return write(() -> target.markDelivered(operationId, quantity));
        }
    }

    private final class GuardedActiveBanks implements ActiveBankStore {

        private final ActiveBankStore target;

        GuardedActiveBanks(ActiveBankStore target) {
            this.target = target;
        }

        @Override
        public Optional<EntityRef> find(UUID playerId) {
            return target.find(playerId);
        }

        @Override
        public void save(UUID playerId, EntityRef entity) {
            write(() -> target.save(playerId, entity));
        }
    }
}
