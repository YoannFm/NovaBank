package dev.novadev.novabank.storage.file;

import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.api.OperationId;
import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.operation.OperationStatus;
import dev.novadev.novabank.api.valuation.ValuationMode;
import dev.novadev.novabank.core.bank.Bank;
import dev.novadev.novabank.core.journal.OperationRecord;
import dev.novadev.novabank.core.repository.ApplyOutcome;
import dev.novadev.novabank.core.repository.BankMutation;
import dev.novadev.novabank.core.repository.BankRepository;
import dev.novadev.novabank.core.repository.MutationDecision;
import dev.novadev.novabank.core.repository.RecordTransition;
import dev.novadev.novabank.core.repository.RepositorySupport;
import java.util.List;
import java.util.Optional;

/**
 * {@link BankRepository} fichier : chaque unite atomique (lecture, decision, ecriture de la
 * banque et de l'entree) s'execute sous le verrou exclusif du stockage et s'enregistre en une
 * seule ligne de journal ; aucun conflit possible, donc jamais de {@code CONCURRENT_MODIFICATION}.
 */
final class FileBankRepository implements BankRepository {

    private final FileStore store;

    FileBankRepository(FileStore store) {
        this.store = store;
    }

    @Override
    public Optional<Bank> findBank(BankId bankId) {
        return store.read(state -> Optional.ofNullable(state.bank(bankId)));
    }

    @Override
    public Bank createBank(BankId bankId, EntityRef owner, ValuationMode valuationMode) {
        return store.write(state -> {
            Bank existing = state.bank(bankId);
            if (existing != null) {
                return existing;
            }
            Bank created = Bank.open(bankId, owner, valuationMode);
            store.commit(Commit.of(created));
            return created;
        });
    }

    @Override
    public Optional<OperationRecord> findOperation(OperationId operationId) {
        return store.read(state -> Optional.ofNullable(state.operation(operationId)));
    }

    @Override
    public ApplyOutcome apply(OperationId operationId, BankId bankId, BankMutation mutation) {
        return store.write(state -> {
            OperationRecord existing = state.operation(operationId);
            if (existing != null) {
                return new ApplyOutcome.Duplicate(existing);
            }
            Bank current = state.bank(bankId);
            if (current == null) {
                return new ApplyOutcome.Rejected(OperationStatus.UNKNOWN_BANK);
            }
            return record(operationId, bankId, current, mutation.decide(current), null);
        });
    }

    @Override
    public ApplyOutcome settle(OperationId operationId, RecordTransition transition) {
        return store.write(state -> {
            OperationRecord record = state.operation(operationId);
            if (record == null) {
                return new ApplyOutcome.Rejected(OperationStatus.UNKNOWN_OPERATION);
            }
            Bank current = state.bank(record.bankId());
            return record(operationId, record.bankId(), current, transition.decide(current, record), record);
        });
    }

    /**
     * Enregistre une decision acceptee (banque et entree en une ligne de journal synchronisee),
     * sous le verrou exclusif deja pris par l'appelant.
     *
     * @param existing entree avant la transition, {@code null} pour une nouvelle operation
     */
    private ApplyOutcome record(OperationId operationId, BankId bankId, Bank current, MutationDecision decision,
                                OperationRecord existing) {
        if (decision instanceof MutationDecision.Accept accept) {
            RepositorySupport.requireSameTarget(accept.record(), operationId, bankId);
            Bank successor = RepositorySupport.successor(current, accept.bank());
            store.commit(Commit.of(successor, accept.record()));
            return new ApplyOutcome.Applied(successor, accept.record());
        }
        return RepositorySupport.outcomeWithoutChange(decision, existing);
    }

    @Override
    public List<OperationRecord> reservedWithdrawals() {
        return store.read(FileState::reservedWithdrawals);
    }

    @Override
    public List<OperationRecord> history(BankId bankId, int offset, int limit) {
        RepositorySupport.requirePage(offset, limit);
        return store.read(state -> state.history(bankId, offset, limit));
    }
}
