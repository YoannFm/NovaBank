package dev.novadev.novabank.bukkit.gui;

import java.util.List;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Nom et description d'une icone de GUI. Seul endroit qui utilise les setters texte
 * ({@code String}) de {@link ItemMeta} : presents de 1.17 a 26.x (Spigot et Paper),
 * deprecies au profit des composants Adventure sur Paper uniquement.
 */
final class ItemTexts {

    private ItemTexts() {
    }

    /**
     * @param stack icone
     * @param name  nom affiche (codes couleur deja convertis)
     * @param lore  lignes de description
     * @return l'icone modifiee
     */
    static ItemStack describe(ItemStack stack, String name, List<String> lore) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.setDisplayName(name);
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.values());
        stack.setItemMeta(meta);
        return stack;
    }
}
