package dev.novadev.novabank.bukkit.gui;

import dev.novadev.novabank.api.item.ItemKey;
import java.util.Optional;

/**
 * Ecran d'edition en jeu (disposition du GUI, items et prix) : un coffre ou chaque clic modifie un brouillon. Sans
 * dependance serveur ; l'affichage et les evenements sont geres par {@link EditorMenus}.
 */
public interface EditorScreen {

    /** @return titre du coffre */
    String title();

    /** @return nombre de cases (multiple de 9, au plus 54) */
    int size();

    /**
     * @param slot case du coffre
     * @return son contenu, vide si la case est vide
     */
    Optional<SlotView> slot(int slot);

    /**
     * @param slot   case cliquee
     * @param click  nature du clic
     * @param inHand item tenu en main par l'administrateur, vide si rien
     */
    void click(int slot, MenuClick click, Optional<ItemKey> inHand);

    /**
     * Clic sur un item de l'INVENTAIRE de l'administrateur (partie basse du coffre). Sans effet par defaut.
     *
     * @param item  item cliquee
     * @param click nature du clic
     */
    default void clickPlayerItem(ItemKey item, MenuClick click) {
    }
}
