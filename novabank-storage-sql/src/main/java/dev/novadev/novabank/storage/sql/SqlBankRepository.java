package dev.novadev.novabank.storage.sql;

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
import dev.novadev.novabank.core.sync.BankChange;
import dev.novadev.novabank.core.sync.BankChangeFeed;
import dev.novadev.novabank.core.text.ConsoleText;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * {@link BankRepository} JDBC.
 *
 * <p>{@link #apply} et {@link #settle} forment chacun une transaction : controle du rejeu,
 * lecture de la banque (verrouillee si le dialecte le demande), mutation pure du coeur,
 * ecriture de la version avec {@code WHERE version = ?}, des seules lignes modifiees et
 * de l'entree de journal, puis commit. Un conflit (version changee, identifiant journalise
 * entre-temps par un autre serveur, interblocage signale par le moteur) annule la transaction
 * et la rejoue (voir {@link SqlTransactions}) ; s'il persiste, l'operation est refusee avec
 * {@link OperationStatus#CONCURRENT_MODIFICATION}, sans rien avoir ecrit. Le rejeu d'un identifiant deja journalise
 * est detecte au debut de chaque tentative. Toute erreur SQL remonte en
 * {@code StorageException} apres rollback : jamais d'etat partiel.
 */
final class SqlBankRepository implements BankRepository, BankChangeFeed {

    private final SqlTransactions transactions;
    private final BankRows banks;
    private final OperationRows operations;

    SqlBankRepository(SqlTransactions transactions, BankRows banks, OperationRows operations) {
        this.transactions = transactions;
        this.banks = banks;
        this.operations = operations;
    }

    @Override
    public Optional<Bank> findBank(BankId bankId) {
        return transactions.read("lecture de la banque " + bankId, connection -> banks.find(connection, bankId, false));
    }

    @Override
    public Bank createBank(BankId bankId, EntityRef owner, ValuationMode valuationMode) {
        return transactions.write("creation de la banque " + bankId, connection -> {
            banks.insertIfAbsent(connection, Bank.open(bankId, owner, valuationMode));
            return banks.find(connection, bankId, false)
                    .orElseThrow(() -> new SQLException(ConsoleText.tr("Banque introuvable apres insertion : {0}",
                            bankId)));
        });
    }

    @Override
    public Optional<OperationRecord> findOperation(OperationId operationId) {
        return transactions.read("lecture de l'operation " + operationId,
                connection -> operations.find(connection, operationId));
    }

    @Override
    public List<OperationRecord> reservedWithdrawals() {
        return transactions.read("liste des retraits reserves", operations::reservedWithdrawals);
    }

    @Override
    public List<OperationRecord> history(BankId bankId, int offset, int limit) {
        RepositorySupport.requirePage(offset, limit);
        return transactions.read("journal de la banque " + bankId,
                connection -> operations.history(connection, bankId, offset, limit));
    }

    @Override
    public List<BankChange> changesSince(Instant since, int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException(ConsoleText.tr("limit doit etre strictement positif : {0}", limit));
        }
        return transactions.read("flux de changements des banques",
                connection -> banks.changesSince(connection, since, limit));
    }

    @Override
    public ApplyOutcome apply(OperationId operationId, BankId bankId, BankMutation mutation) {
        return transactions.writeOrConflict("operation " + operationId,
                connection -> applyOnce(connection, operationId, bankId, mutation), SqlBankRepository::conflict);
    }

    @Override
    public ApplyOutcome settle(OperationId operationId, RecordTransition transition) {
        if (findOperation(operationId).isEmpty()) {
            return new ApplyOutcome.Rejected(OperationStatus.UNKNOWN_OPERATION);
        }
        return transactions.writeOrConflict("solde de l'operation " + operationId,
                connection -> settleOnce(connection, operationId, transition), SqlBankRepository::conflict);
    }

    private ApplyOutcome applyOnce(Connection connection, OperationId operationId, BankId bankId,
                                   BankMutation mutation) throws SQLException {
        Optional<OperationRecord> existing = operations.find(connection, operationId);
        if (existing.isPresent()) {
            return new ApplyOutcome.Duplicate(existing.get());
        }
        Optional<Bank> current = banks.find(connection, bankId, true);
        if (current.isEmpty()) {
            return new ApplyOutcome.Rejected(OperationStatus.UNKNOWN_BANK);
        }
        MutationDecision decision = mutation.decide(current.get());
        if (decision instanceof MutationDecision.Accept accept) {
            RepositorySupport.requireSameTarget(accept.record(), operationId, bankId);
            Bank saved = banks.saveSuccessor(connection, current.get(), accept.bank());
            operations.insert(connection, accept.record());
            return new ApplyOutcome.Applied(saved, accept.record());
        }
        return RepositorySupport.outcomeWithoutChange(decision, null);
    }

    private ApplyOutcome settleOnce(Connection connection, OperationId operationId, RecordTransition transition)
            throws SQLException {
        OperationRecord record = operations.find(connection, operationId)
                .orElseThrow(() -> new SQLException(ConsoleText.tr("Operation disparue du journal : {0}",
                        operationId)));
        Bank current = banks.find(connection, record.bankId(), true)
                .orElseThrow(() -> new SQLException(ConsoleText.tr("Banque de l'operation introuvable : {0}",
                        record.bankId())));
        MutationDecision decision = transition.decide(current, record);
        if (decision instanceof MutationDecision.Accept accept) {
            RepositorySupport.requireSameTarget(accept.record(), operationId, record.bankId());
            Bank saved = banks.saveSuccessor(connection, current, accept.bank());
            operations.update(connection, accept.record());
            return new ApplyOutcome.Applied(saved, accept.record());
        }
        return RepositorySupport.outcomeWithoutChange(decision, record);
    }

    private static ApplyOutcome conflict() {
        return new ApplyOutcome.Rejected(OperationStatus.CONCURRENT_MODIFICATION);
    }
}
