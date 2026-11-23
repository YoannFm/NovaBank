package dev.novadev.novabank.bukkit.gui;

import dev.novadev.novabank.api.entity.EntityRef;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Contenu a afficher.
 *
 * @param bank  banque active
 * @param value valeur de la banque
 * @param page  page du stock
 */
public record MenuModel(EntityRef bank, BigDecimal value, StockPage page) {

    /**
     * @throws NullPointerException si un champ est nul
     */
    public MenuModel {
        Objects.requireNonNull(bank, "bank");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(page, "page");
    }
}
