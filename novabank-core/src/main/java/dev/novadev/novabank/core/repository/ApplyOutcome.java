package dev.novadev.novabank.core.repository;

import dev.novadev.novabank.api.operation.OperationStatus;
import dev.novadev.novabank.core.bank.Bank;
import dev.novadev.novabank.core.journal.OperationRecord;
import java.util.Objects;

/**
 * Issue d'une mutation soumise au {@link BankRepository}.
 */
public sealed interface ApplyOutcome {

    /**
     * La mutation est enregistree.
     *
     * @param bank   etat enregistre de la banque, version incrementee
     * @param record entree de journal enregistree
     */
    record Applied(Bank bank, OperationRecord record) implements ApplyOutcome {

        /** Construit une issue appliquee. */
        public Applied {
            Objects.requireNonNull(bank, "bank");
            Objects.requireNonNull(record, "record");
        }
    }

    /**
     * L'operation etait deja enregistree : rien n'a change.
     *
     * @param record entree de journal existante
     */
    record Duplicate(OperationRecord record) implements ApplyOutcome {

        /** Construit une issue de rejeu. */
        public Duplicate {
            Objects.requireNonNull(record, "record");
        }
    }

    /**
     * L'operation est refusee : rien n'a change.
     *
     * @param status motif du refus
     */
    record Rejected(OperationStatus status) implements ApplyOutcome {

        /** Construit une issue de refus. */
        public Rejected {
            Objects.requireNonNull(status, "status");
        }
    }
}
