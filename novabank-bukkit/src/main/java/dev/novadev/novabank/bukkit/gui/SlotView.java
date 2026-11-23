package dev.novadev.novabank.bukkit.gui;

import dev.novadev.novabank.api.item.ItemKey;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Ce qu'une case d'un ecran d'edition affiche.
 *
 * @param icon item affiche
 * @param name nom (codes couleur '&amp;' admis)
 * @param lore description, une ligne par element
 */
public record SlotView(ItemKey icon, String name, List<String> lore) {

    /** Verifie la presence des champs. */
    public SlotView {
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(name, "name");
        lore = List.copyOf(lore);
    }

    /**
     * @param text texte d'une cle de langue (lignes separees par un saut de ligne)
     * @return une ligne de description par ligne du texte, aucune si le texte est vide
     */
    static List<String> lines(String text) {
        return text.isEmpty() ? List.of() : Arrays.asList(text.split("\n", -1));
    }
}
