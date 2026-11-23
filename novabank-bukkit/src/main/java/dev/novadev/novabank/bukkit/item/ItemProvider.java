package dev.novadev.novabank.bukkit.item;

import dev.novadev.novabank.api.item.ItemKey;
import java.util.Optional;
import org.bukkit.inventory.ItemStack;

/**
 * Fournisseur d'items d'un namespace : reconnait ses items dans un inventaire et les
 * recree au retrait. Appele uniquement sur le thread du joueur.
 */
public interface ItemProvider {

    /**
     * @return namespace des cles gerees, par exemple {@code minecraft}
     */
    String namespace();

    /**
     * Identifie une pile. Une pile non reconnue, ou reconnue mais modifiee (renommee,
     * enchantee...), donne vide : elle n'est jamais comptee ni retiree.
     *
     * @param stack pile, eventuellement {@code null} ou vide
     * @return la cle de l'item, ou vide
     */
    Optional<ItemKey> identify(ItemStack stack);

    /**
     * Cree une pile neuve de l'item.
     *
     * @param key    cle du namespace de ce fournisseur
     * @param amount quantite, entre 1 et {@link #maxStackSize(ItemKey)}
     * @return la pile, ou vide si l'item n'existe pas (ou plus) sur ce serveur
     */
    Optional<ItemStack> create(ItemKey key, int amount);

    /**
     * @param key cle du namespace de ce fournisseur
     * @return taille maximale d'une pile de cet item (1 si l'item est inconnu)
     */
    int maxStackSize(ItemKey key);
}
