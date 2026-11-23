package dev.novadev.novabank.core.application;

import dev.novadev.novabank.api.item.ItemKey;
import java.util.Optional;
import java.util.UUID;

/**
 * Port : inventaire d'un joueur connecte.
 *
 * <p>Appele UNIQUEMENT sur le thread du joueur ({@link TaskScheduler#runForPlayer}), jamais
 * depuis l'executeur d'entrees/sorties. Seuls les items <em>identifies</em> comptent : un
 * item vanilla renomme, enchante, porteur d'un lore ou d'une durabilite modifiee n'est
 * jamais compte ni retire (decision D12).
 */
public interface InventoryGateway {

    /**
     * @param playerId joueur connecte
     * @param item     item recherche
     * @return nombre d'unites identifiees de cet item dans l'inventaire
     */
    long count(UUID playerId, ItemKey item);

    /**
     * Retire au plus {@code max} unites identifiees de l'item.
     *
     * @param playerId joueur connecte
     * @param item     item a retirer
     * @param max      quantite maximale, positive
     * @return quantite reellement retiree
     */
    long remove(UUID playerId, ItemKey item, long max);

    /**
     * Donne des unites de l'item, dans la limite de la place disponible.
     *
     * @param playerId joueur connecte
     * @param item     item a donner
     * @param quantity quantite, positive
     * @return quantite NON donnee (0 si tout a ete donne)
     */
    long give(UUID playerId, ItemKey item, long quantity);

    /**
     * @param playerId joueur connecte
     * @return l'item identifie tenu en main principale, ou vide (main vide, item non
     *         identifie ou modifie)
     */
    Optional<ItemKey> heldItem(UUID playerId);
}
