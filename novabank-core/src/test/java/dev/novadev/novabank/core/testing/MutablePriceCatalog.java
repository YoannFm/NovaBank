package dev.novadev.novabank.core.testing;

import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.core.valuation.PriceCatalog;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link PriceCatalog} modifiable, pour simuler un changement de prix ou le retrait
 * d'un item du catalogue.
 */
public final class MutablePriceCatalog implements PriceCatalog {

    private final Map<ItemKey, BigDecimal> prices = new ConcurrentHashMap<>();

    @Override
    public Optional<BigDecimal> unitPrice(ItemKey item) {
        return Optional.ofNullable(prices.get(item));
    }

    /**
     * @param item  item
     * @param price prix unitaire, au format decimal texte
     * @return ce catalogue
     */
    public MutablePriceCatalog price(ItemKey item, String price) {
        prices.put(item, new BigDecimal(price));
        return this;
    }

    /**
     * @param item item a retirer du catalogue
     */
    public void remove(ItemKey item) {
        prices.remove(item);
    }
}
