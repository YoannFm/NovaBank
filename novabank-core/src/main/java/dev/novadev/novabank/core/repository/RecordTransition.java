package dev.novadev.novabank.core.repository;

import dev.novadev.novabank.core.bank.Bank;
import dev.novadev.novabank.core.journal.OperationRecord;

/**
 * Transition d'une operation deja journalisee (confirmation ou annulation de retrait).
 *
 * <p>Meme exigence de purete que {@link BankMutation}.
 */
@FunctionalInterface
public interface RecordTransition {

    /**
     * @param current etat courant de la banque de l'operation, lu dans l'unite atomique
     * @param record  entree de journal courante, lue dans la meme unite atomique
     * @return la decision a appliquer
     */
    MutationDecision decide(Bank current, OperationRecord record);
}
