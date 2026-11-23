package dev.novadev.novabank.bukkit.gui;

import dev.novadev.novabank.api.item.ItemKey;

/**
 * Place disponible dans l'inventaire du joueur qui clique (lue sur son thread).
 */
public interface InventoryRoom {

    /**
     * @param item item
     * @return taille maximale d'une pile de cet item
     */
    int maxStackSize(ItemKey item);

    /**
     * @param item item
     * @return unites de cet item qui tiennent encore dans l'inventaire
     */
    long room(ItemKey item);
}
