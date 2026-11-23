package dev.novadev.novabank.core.sync;

import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.api.OperationId;
import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.valuation.ValuationMode;
import dev.novadev.novabank.core.bank.Bank;
import dev.novadev.novabank.core.journal.OperationRecord;
import dev.novadev.novabank.core.repository.ApplyOutcome;
import dev.novadev.novabank.core.repository.BankMutation;
import dev.novadev.novabank.core.repository.BankRepository;
import dev.novadev.novabank.core.repository.RecordTransition;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Decore un {@link BankRepository} : chaque mutation acceptee est signalee au
 * {@link SyncCoordinator} une fois le stockage valide. Le contrat du port est inchange.
 */
public final class PublishingBankRepository implements BankRepository {

    private final BankRepository delegate;
    private final LocalChangeSink coordinator;
    private final Clock clock;

    /**
     * @param delegate    depot reel
     * @param coordinator destinataire des changements enregistres
     * @param clock       horloge de la date des changements
     */
    public PublishingBankRepository(BankRepository delegate, LocalChangeSink coordinator, Clock clock) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Optional<Bank> findBank(BankId bankId) {
        return delegate.findBank(bankId);
    }

    @Override
    public Bank createBank(BankId bankId, EntityRef owner, ValuationMode valuationMode) {
        boolean existed = delegate.findBank(bankId).isPresent();
        Bank bank = delegate.createBank(bankId, owner, valuationMode);
        if (!existed) {
            try {
                coordinator.bankCreated(bank);
            } catch (RuntimeException failure) {
                coordinator.noteAnnounceFailure(failure);
            }
        }
        return bank;
    }

    @Override
    public Optional<OperationRecord> findOperation(OperationId operationId) {
        return delegate.findOperation(operationId);
    }

    @Override
    public ApplyOutcome apply(OperationId operationId, BankId bankId, BankMutation mutation) {
        return announce(delegate.apply(operationId, bankId, mutation));
    }

    @Override
    public ApplyOutcome settle(OperationId operationId, RecordTransition transition) {
        return announce(delegate.settle(operationId, transition));
    }

    @Override
    public List<OperationRecord> reservedWithdrawals() {
        return delegate.reservedWithdrawals();
    }

    @Override
    public List<OperationRecord> history(BankId bankId, int offset, int limit) {
        return delegate.history(bankId, offset, limit);
    }

    private ApplyOutcome announce(ApplyOutcome outcome) {
        if (outcome instanceof ApplyOutcome.Applied applied) {
            try {
                coordinator.localChange(new BankChange(applied.bank().id(), applied.bank().version(),
                        applied.bank().resetEpoch(), clock.instant()));
                coordinator.applied(applied.bank(), applied.record());
            } catch (RuntimeException failure) {
                // La notification est secondaire : l'operation est deja enregistree.
                coordinator.noteAnnounceFailure(failure);
            }
        }
        return outcome;
    }
}
