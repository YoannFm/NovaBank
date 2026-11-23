package dev.novadev.novabank.core.repository;

import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.api.OperationId;
import dev.novadev.novabank.core.bank.Bank;
import dev.novadev.novabank.core.journal.OperationRecord;
import dev.novadev.novabank.core.text.ConsoleText;

/**
 * Regles du contrat {@link BankRepository} communes a toutes les implementations,
 * pour qu'aucun backend ne les reecrive a sa facon.
 */
public final class RepositorySupport {

    private RepositorySupport() {
    }

    /**
     * Verifie qu'une entree acceptee vise bien l'operation et la banque demandees.
     *
     * @param record      entree produite par la mutation
     * @param operationId operation demandee
     * @param bankId      banque demandee
     * @throws IllegalStateException si l'entree est hors cible
     */
    public static void requireSameTarget(OperationRecord record, OperationId operationId, BankId bankId) {
        if (!record.operationId().equals(operationId) || !record.bankId().equals(bankId)) {
            throw new IllegalStateException(ConsoleText.tr("Entree de journal hors cible : {0}", record));
        }
    }

    /**
     * Etat a enregistrer apres une mutation acceptee : meme identite, version lue + 1.
     *
     * @param current etat lu dans l'unite atomique
     * @param updated etat produit par la mutation
     * @return l'etat a enregistrer
     * @throws IllegalStateException si la mutation a change l'identifiant, le proprietaire
     *                               ou le mode de valorisation de la banque
     */
    public static Bank successor(Bank current, Bank updated) {
        if (!updated.id().equals(current.id()) || !updated.owner().equals(current.owner())
                || updated.valuationMode() != current.valuationMode()) {
            throw new IllegalStateException(ConsoleText.tr("La mutation a change l'identite de la banque : {0}",
                    updated.id()));
        }
        return updated.withVersion(Math.addExact(current.version(), 1L));
    }

    /**
     * Verifie les bornes d'une page de journal.
     *
     * @param offset nombre d'entrees a sauter
     * @param limit  nombre maximal d'entrees
     * @throws IllegalArgumentException si {@code offset < 0} ou {@code limit < 1}
     */
    public static void requirePage(int offset, int limit) {
        if (offset < 0 || limit < 1) {
            throw new IllegalArgumentException(ConsoleText.tr("Page de journal invalide : offset {0}, limite {1}",
                    offset, limit));
        }
    }

    /**
     * Issue d'une decision qui ne modifie rien (refus ou rejeu).
     *
     * @param decision decision autre qu'une acceptation
     * @param record   entree courante pour un rejeu, {@code null} pour une operation nouvelle
     * @return {@link ApplyOutcome.Rejected} ou {@link ApplyOutcome.Duplicate}
     * @throws IllegalStateException si un rejeu est signale pour une operation nouvelle,
     *                               ou si la decision est une acceptation
     */
    public static ApplyOutcome outcomeWithoutChange(MutationDecision decision, OperationRecord record) {
        if (decision instanceof MutationDecision.Reject reject) {
            return new ApplyOutcome.Rejected(reject.status());
        }
        if (decision instanceof MutationDecision.Accept) {
            throw new IllegalStateException(ConsoleText.tr("Une acceptation modifie la banque"));
        }
        if (record == null) {
            throw new IllegalStateException(ConsoleText.tr("Rejeu signale pour une operation nouvelle"));
        }
        return new ApplyOutcome.Duplicate(record);
    }
}
