package dev.novadev.novabank.core.delivery;

import dev.novadev.novabank.api.OperationId;
import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.core.text.ConsoleText;
import java.util.Objects;
import java.util.UUID;

/**
 * Items dus a un joueur : retrait partiellement livre, ou depot refuse dont les items
 * n'ont pas pu etre rendus (joueur deconnecte, inventaire plein).
 *
 * @param operationId operation d'origine (une seule livraison par operation)
 * @param playerId    joueur destinataire
 * @param itemKey     item du
 * @param quantity    quantite due (restant due a la relecture), positive
 */
public record PendingDelivery(OperationId operationId, UUID playerId, ItemKey itemKey, long quantity) {

    /**
     * Construit une livraison en attente.
     *
     * @throws IllegalArgumentException si la quantite n'est pas positive
     */
    public PendingDelivery {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(itemKey, "itemKey");
        if (quantity <= 0) {
            throw new IllegalArgumentException(ConsoleText.tr("Quantite a livrer non positive : {0}", quantity));
        }
    }
}
