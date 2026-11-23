package dev.novadev.novabank.core.ledger;

import dev.novadev.novabank.api.operation.OperationStatus;
import dev.novadev.novabank.core.repository.MutationDecision;
import java.util.function.Supplier;

/**
 * Transforme un debordement de quantite en refus propre.
 */
final class OverflowGuard {

    private OverflowGuard() {
    }

    static MutationDecision decide(Supplier<MutationDecision> decision) {
        try {
            return decision.get();
        } catch (ArithmeticException overflow) {
            return MutationDecision.reject(OperationStatus.QUANTITY_OVERFLOW);
        }
    }
}
