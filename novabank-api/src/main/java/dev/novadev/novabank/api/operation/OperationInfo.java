package dev.novadev.novabank.api.operation;

import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.api.OperationId;
import dev.novadev.novabank.api.item.ItemKey;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Etat d'une operation du journal.
 *
 * @param id         identifiant de l'operation
 * @param type       nature
 * @param bank       banque concernee
 * @param item       item deplace, {@code null} pour une reinitialisation
 * @param quantity   unites deplacees
 * @param valueDelta variation de valeur de la banque
 * @param state      etat du journal : {@code COMMITTED}, {@code RESERVED} (retrait en cours), {@code CANCELLED}...
 * @param at         horodatage du mouvement initial
 */
public record OperationInfo(OperationId id, OperationType type, BankId bank, ItemKey item, long quantity,
                            BigDecimal valueDelta, String state, Instant at) {

    /**
     * @throws NullPointerException si un champ obligatoire est nul
     */
    public OperationInfo {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(bank, "bank");
        Objects.requireNonNull(valueDelta, "valueDelta");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(at, "at");
    }
}
