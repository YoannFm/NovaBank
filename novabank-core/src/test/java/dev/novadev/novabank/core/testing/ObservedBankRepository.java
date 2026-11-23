package dev.novadev.novabank.core.testing;

import static org.assertj.core.api.Assertions.assertThat;

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
import dev.novadev.novabank.core.repository.StorageException;
import java.util.List;
import java.util.Optional;

/**
 * {@link BankRepository} en memoire instrumente : verifie que chaque appel a lieu sur
 * l'executeur IO, journalise les ecritures validees et simule des pannes.
 *
 * <p>Pannes : {@link #failNextWrites(int)} fait echouer les prochaines ecritures AVANT
 * tout effet ; {@link #failAfterCommit(int)} les valide puis leve l'exception (reponse
 * perdue) ; {@link #failEverything(boolean)} fait echouer tous les appels, lectures comprises.
 */
public final class ObservedBankRepository implements BankRepository {

    private final InMemoryBankRepository delegate = new InMemoryBankRepository();
    private final EventLog log;
    private int failBefore;
    private int failAfter;
    private boolean failEverything;

    /**
     * @param log journal des evenements
     */
    public ObservedBankRepository(EventLog log) {
        this.log = log;
    }

    /**
     * @param count nombre d'ecritures a faire echouer sans effet
     */
    public void failNextWrites(int count) {
        failBefore = count;
    }

    /**
     * @param count nombre d'ecritures a valider puis faire echouer
     */
    public void failAfterCommit(int count) {
        failAfter = count;
    }

    /**
     * @param fail faire echouer tous les appels
     */
    public void failEverything(boolean fail) {
        failEverything = fail;
    }

    /**
     * @return le depot sous-jacent, pour les lectures de verification hors contexte IO
     */
    public InMemoryBankRepository direct() {
        return delegate;
    }

    @Override
    public Optional<Bank> findBank(BankId bankId) {
        check();
        return delegate.findBank(bankId);
    }

    @Override
    public Bank createBank(BankId bankId, EntityRef owner, ValuationMode valuationMode) {
        check();
        return delegate.createBank(bankId, owner, valuationMode);
    }

    @Override
    public Optional<OperationRecord> findOperation(OperationId operationId) {
        check();
        return delegate.findOperation(operationId);
    }

    @Override
    public ApplyOutcome apply(OperationId operationId, BankId bankId, BankMutation mutation) {
        beforeWrite();
        ApplyOutcome outcome = delegate.apply(operationId, bankId, mutation);
        return afterWrite("base:apply:" + operationId, outcome);
    }

    @Override
    public ApplyOutcome settle(OperationId operationId, RecordTransition transition) {
        beforeWrite();
        ApplyOutcome outcome = delegate.settle(operationId, transition);
        return afterWrite("base:settle:" + operationId, outcome);
    }

    @Override
    public List<OperationRecord> reservedWithdrawals() {
        check();
        return delegate.reservedWithdrawals();
    }

    @Override
    public List<OperationRecord> history(BankId bankId, int offset, int limit) {
        check();
        return delegate.history(bankId, offset, limit);
    }

    private void check() {
        assertThat(ManualScheduler.context()).as("acces base hors executeur IO")
                .isIn(ManualScheduler.Context.IO, ManualScheduler.Context.NONE);
        if (failEverything) {
            throw new StorageException("panne simulee");
        }
    }

    private void beforeWrite() {
        check();
        if (failBefore > 0) {
            failBefore--;
            throw new StorageException("panne simulee avant ecriture");
        }
    }

    private ApplyOutcome afterWrite(String event, ApplyOutcome outcome) {
        if (outcome instanceof ApplyOutcome.Applied) {
            log.add(event);
        }
        if (failAfter > 0) {
            failAfter--;
            throw new StorageException("panne simulee apres validation");
        }
        return outcome;
    }
}
