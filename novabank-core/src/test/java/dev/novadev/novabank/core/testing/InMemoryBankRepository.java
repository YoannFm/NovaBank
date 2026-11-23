package dev.novadev.novabank.core.testing;

import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.api.OperationId;
import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.operation.OperationStatus;
import dev.novadev.novabank.api.operation.OperationType;
import dev.novadev.novabank.api.valuation.ValuationMode;
import dev.novadev.novabank.core.bank.Bank;
import dev.novadev.novabank.core.journal.OperationRecord;
import dev.novadev.novabank.core.journal.OperationState;
import dev.novadev.novabank.core.repository.ApplyOutcome;
import dev.novadev.novabank.core.repository.BankMutation;
import dev.novadev.novabank.core.repository.BankRepository;
import dev.novadev.novabank.core.repository.MutationDecision;
import dev.novadev.novabank.core.repository.RecordTransition;
import dev.novadev.novabank.core.repository.RepositorySupport;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * {@link BankRepository} en memoire, avec un verrou exclusif par banque.
 *
 * <p>Toute ecriture d'une banque ou d'une entree de son journal se fait sous le verrou
 * de cette banque, lectures de banque comprises : une lecture ne voit jamais l'entree
 * de journal sans la banque correspondante. L'unicite globale des identifiants
 * d'operation (meme entre deux banques) est garantie par un {@code putIfAbsent}.
 */
public final class InMemoryBankRepository implements BankRepository {

    private final Map<BankId, Bank> banks = new ConcurrentHashMap<>();
    private final Map<OperationId, OperationRecord> journal = new ConcurrentHashMap<>();
    private final Map<BankId, ReentrantLock> locks = new ConcurrentHashMap<>();

    @Override
    public Optional<Bank> findBank(BankId bankId) {
        return locked(bankId, () -> Optional.ofNullable(banks.get(bankId)));
    }

    @Override
    public Bank createBank(BankId bankId, EntityRef owner, ValuationMode valuationMode) {
        return locked(bankId, () -> banks.computeIfAbsent(bankId, id -> Bank.open(id, owner, valuationMode)));
    }

    @Override
    public Optional<OperationRecord> findOperation(OperationId operationId) {
        return Optional.ofNullable(journal.get(operationId));
    }

    @Override
    public ApplyOutcome apply(OperationId operationId, BankId bankId, BankMutation mutation) {
        return locked(bankId, () -> {
            OperationRecord existing = journal.get(operationId);
            if (existing != null) {
                return new ApplyOutcome.Duplicate(existing);
            }
            Bank current = banks.get(bankId);
            if (current == null) {
                return new ApplyOutcome.Rejected(OperationStatus.UNKNOWN_BANK);
            }
            MutationDecision decision = mutation.decide(current);
            if (decision instanceof MutationDecision.Accept accept) {
                RepositorySupport.requireSameTarget(accept.record(), operationId, bankId);
                Bank successor = RepositorySupport.successor(current, accept.bank());
                OperationRecord concurrent = journal.putIfAbsent(operationId, accept.record());
                if (concurrent != null) {
                    return new ApplyOutcome.Duplicate(concurrent);
                }
                banks.put(bankId, successor);
                return new ApplyOutcome.Applied(successor, accept.record());
            }
            return RepositorySupport.outcomeWithoutChange(decision, null);
        });
    }

    @Override
    public ApplyOutcome settle(OperationId operationId, RecordTransition transition) {
        OperationRecord known = journal.get(operationId);
        if (known == null) {
            return new ApplyOutcome.Rejected(OperationStatus.UNKNOWN_OPERATION);
        }
        BankId bankId = known.bankId();
        return locked(bankId, () -> {
            OperationRecord record = journal.get(operationId);
            Bank current = banks.get(bankId);
            MutationDecision decision = transition.decide(current, record);
            if (decision instanceof MutationDecision.Accept accept) {
                RepositorySupport.requireSameTarget(accept.record(), operationId, bankId);
                Bank successor = RepositorySupport.successor(current, accept.bank());
                journal.put(operationId, accept.record());
                banks.put(bankId, successor);
                return new ApplyOutcome.Applied(successor, accept.record());
            }
            return RepositorySupport.outcomeWithoutChange(decision, record);
        });
    }

    @Override
    public List<OperationRecord> reservedWithdrawals() {
        return journal.values().stream()
                .filter(record -> record.type() == OperationType.WITHDRAW && record.state() == OperationState.RESERVED)
                .sorted(Comparator.comparing(OperationRecord::timestamp)
                        .thenComparing(record -> record.operationId().toString()))
                .toList();
    }

    @Override
    public List<OperationRecord> history(BankId bankId, int offset, int limit) {
        RepositorySupport.requirePage(offset, limit);
        return journal.values().stream()
                .filter(record -> record.bankId().equals(bankId))
                .sorted(Comparator.comparing(OperationRecord::timestamp)
                        .thenComparing(record -> record.operationId().toString())
                        .reversed())
                .skip(offset)
                .limit(limit)
                .toList();
    }

    /**
     * Copie du journal, pour les verifications des tests.
     *
     * @return toutes les entrees enregistrees
     */
    public List<OperationRecord> journal() {
        return List.copyOf(journal.values());
    }

    /**
     * Copie des banques, pour l'export du contenu.
     *
     * @return toutes les banques
     */
    public List<Bank> banks() {
        return List.copyOf(banks.values());
    }

    /**
     * Enregistre une banque telle quelle (import du contenu).
     *
     * @param bank banque
     */
    public void restore(Bank bank) {
        locked(bank.id(), () -> banks.put(bank.id(), bank));
    }

    /**
     * Enregistre une entree telle quelle (import du contenu).
     *
     * @param record entree
     */
    public void restore(OperationRecord record) {
        journal.put(record.operationId(), record);
    }

    private <T> T locked(BankId bankId, Supplier<T> action) {
        ReentrantLock lock = locks.computeIfAbsent(bankId, id -> new ReentrantLock());
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }
}
