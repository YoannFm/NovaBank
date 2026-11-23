package dev.novadev.novabank.bukkit.config;

/**
 * Indique si un item vanilla existe dans la version du serveur.
 */
@FunctionalInterface
public interface VanillaItems {

    /**
     * @param namespacedId cle complete, par exemple {@code minecraft:copper_block}
     * @return {@code true} si le materiau existe et est un item
     */
    boolean isItem(String namespacedId);
}
