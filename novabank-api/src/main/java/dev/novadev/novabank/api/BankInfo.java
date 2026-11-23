package dev.novadev.novabank.api;

import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.api.valuation.ValuationMode;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

/**
 * Photographie d'une banque a un instant donne.
 *
 * @param id      identifiant stable de la banque
 * @param owner   entite proprietaire
 * @param mode    mode de valorisation
 * @param value   valeur courante, au catalogue de prix du serveur
 * @param stock   unites en stock par item (items absents : zero)
 * @param version version de la banque (augmente a chaque operation enregistree)
 */
public record BankInfo(BankId id, EntityRef owner, ValuationMode mode, BigDecimal value, Map<ItemKey, Long> stock,
                       long version) {

    /**
     * Copie defensive du stock.
     *
     * @throws NullPointerException si un champ est nul
     */
    public BankInfo {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(value, "value");
        stock = Map.copyOf(stock);
    }
}
