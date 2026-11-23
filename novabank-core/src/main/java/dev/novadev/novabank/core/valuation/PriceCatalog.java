package dev.novadev.novabank.core.valuation;

import dev.novadev.novabank.api.item.ItemKey;
import java.math.BigDecimal;
import java.util.Optional;

/**
 * Port : prix unitaire courant des items acceptes.
 *
 * <p>Un item absent du catalogue n'est pas accepte en depot ; s'il est deja en stock,
 * il reste retirable et vaut zero en mode {@code DERIVED}. Un prix est toujours
 * positif ou nul.
 */
@FunctionalInterface
public interface PriceCatalog {

    /**
     * Prix unitaire courant d'un item.
     *
     * @param item item recherche
     * @return le prix (positif ou nul), ou vide si l'item n'est pas accepte
     */
    Optional<BigDecimal> unitPrice(ItemKey item);
}
