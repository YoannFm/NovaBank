package dev.novadev.novabank.bukkit.gui;

import dev.novadev.novabank.api.item.ItemKey;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Ligne de stock affichee.
 *
 * @param item     item
 * @param quantity quantite en banque
 * @param value    valeur de la ligne
 */
public record StockEntry(ItemKey item, long quantity, BigDecimal value) {

    /**
     * @throws NullPointerException si un champ est nul
     */
    public StockEntry {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(value, "value");
    }
}
