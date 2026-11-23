package dev.novadev.novabank.config.model;

import dev.novadev.novabank.api.item.ItemKey;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * Entree de la section {@code items} : item accepte en depot et son prix.
 *
 * @param key         cle de l'item
 * @param price       prix unitaire exact tel qu'ecrit dans le fichier (positif ou nul)
 * @param category    categorie optionnelle (classements par categorie)
 * @param displayName nom affiche optionnel
 */
public record AcceptedItem(ItemKey key, BigDecimal price, Optional<String> category, Optional<String> displayName) {

    /**
     * Verifie la presence des champs.
     *
     * @throws NullPointerException si un champ est nul
     */
    public AcceptedItem {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(displayName, "displayName");
    }
}
