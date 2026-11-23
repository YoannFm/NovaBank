package dev.novadev.novabank.core.repository;

import dev.novadev.novabank.api.operation.OperationStatus;
import dev.novadev.novabank.core.bank.Bank;
import dev.novadev.novabank.core.journal.OperationRecord;
import java.util.Objects;

/**
 * Decision prise par une mutation sur l'etat courant d'une banque, que le
 * {@link BankRepository} applique ensuite atomiquement.
 */
public sealed interface MutationDecision {

    /**
     * Enregistrer le nouvel etat de banque et l'entree de journal.
     *
     * @param bank   nouvel etat de la banque (le stockage fixe la version)
     * @param record entree de journal a creer ou remplacer
     */
    record Accept(Bank bank, OperationRecord record) implements MutationDecision {

        /** Construit une decision d'acceptation. */
        public Accept {
            Objects.requireNonNull(bank, "bank");
            Objects.requireNonNull(record, "record");
        }
    }

    /**
     * Ne rien modifier et refuser l'operation.
     *
     * @param status motif du refus
     */
    record Reject(OperationStatus status) implements MutationDecision {

        /** Construit une decision de refus. */
        public Reject {
            Objects.requireNonNull(status, "status");
        }
    }

    /**
     * Ne rien modifier : l'etape demandee est deja appliquee (rejeu).
     */
    record AlreadyApplied() implements MutationDecision {
    }

    /**
     * @param bank   nouvel etat de la banque
     * @param record entree de journal
     * @return une decision d'acceptation
     */
    static MutationDecision accept(Bank bank, OperationRecord record) {
        return new Accept(bank, record);
    }

    /**
     * @param status motif du refus
     * @return une decision de refus
     */
    static MutationDecision reject(OperationStatus status) {
        return new Reject(status);
    }

    /**
     * @return une decision de rejeu sans effet
     */
    static MutationDecision alreadyApplied() {
        return new AlreadyApplied();
    }
}
