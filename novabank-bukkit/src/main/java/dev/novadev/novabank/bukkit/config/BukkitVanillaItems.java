package dev.novadev.novabank.bukkit.config;

import org.bukkit.Material;

/**
 * Items vanilla selon le serveur : resolution par nom ({@link Material#matchMaterial(String)}),
 * sans {@code Material.values()} ni {@code switch} sur {@code Material} (HANDOFF 2.2).
 */
public final class BukkitVanillaItems implements VanillaItems {

    @Override
    public boolean isItem(String namespacedId) {
        Material material = Material.matchMaterial(namespacedId);
        return material != null && material.isItem();
    }
}
