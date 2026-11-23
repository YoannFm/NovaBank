package dev.novadev.novabank.storage.mongo;

import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.api.OperationId;
import dev.novadev.novabank.core.repository.ApplyOutcome;
import dev.novadev.novabank.core.repository.BankMutation;
import dev.novadev.novabank.core.repository.RecordTransition;
import java.util.Optional;

/**
 * Strategie d'ecriture atomique des operations, choisie selon le serveur : transactions
 * multi-documents ({@link TransactionalBankWrites}) ou journal en attente ({@link PendingBankWrites}).
 *
 * <p>Chaque methode est UNE tentative : elle renvoie vide, sans aucun effet durable, si elle a
 * perdu un conflit et doit etre rejouee. Les erreurs du pilote remontent telles quelles.
 */
interface BankWrites {

    /**
     * @return une tentative de {@link dev.novadev.novabank.core.repository.BankRepository#apply}
     */
    Optional<ApplyOutcome> applyOnce(OperationId operationId, BankId bankId, BankMutation mutation);

    /**
     * @return une tentative de {@link dev.novadev.novabank.core.repository.BankRepository#settle}
     */
    Optional<ApplyOutcome> settleOnce(OperationId operationId, RecordTransition transition);
}
