package dev.novadev.novabank.storage.sql;

import dev.novadev.novabank.api.OperationId;
import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.core.journal.OperationRecord;
import dev.novadev.novabank.core.repository.BankMutation;
import dev.novadev.novabank.core.repository.MutationDecision;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Mutations minimales pour tester le depot directement, sans le service metier.
 */
final class TestMutations {

    static final ItemKey STONE = ItemKey.minecraft("stone");

    private TestMutations() {
    }

    /**
     * @return un depot de {@code quantity} pierres, valeur = quantite
     */
    static BankMutation depositOf(OperationId id, long quantity) {
        return bank -> MutationDecision.accept(bank.withLine(STONE, bank.line(STONE).plus(quantity)),
                OperationRecord.deposit(id, bank.id(), STONE, quantity, quantity, BigDecimal.valueOf(quantity),
                        Instant.EPOCH));
    }
}
