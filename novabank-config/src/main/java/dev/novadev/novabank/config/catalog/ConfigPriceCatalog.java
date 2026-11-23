package dev.novadev.novabank.config.catalog;

import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.config.model.AcceptedItem;
import dev.novadev.novabank.core.text.ConsoleText;
import dev.novadev.novabank.core.valuation.PriceCatalog;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Catalogue de prix immuable construit depuis la section {@code items} validee.
 * Les prix sont conserves exactement tels qu'ecrits dans la configuration.
 */
public final class ConfigPriceCatalog implements PriceCatalog {

    private final Map<ItemKey, BigDecimal> prices;

    private ConfigPriceCatalog(Map<ItemKey, BigDecimal> prices) {
        this.prices = Map.copyOf(prices);
    }

    /**
     * Construit le catalogue des items acceptes.
     *
     * @param items items acceptes, cles uniques
     * @return le catalogue
     * @throws IllegalArgumentException si une cle apparait deux fois
     */
    public static ConfigPriceCatalog of(List<AcceptedItem> items) {
        Map<ItemKey, BigDecimal> prices = new HashMap<>();
        for (AcceptedItem item : items) {
            if (prices.putIfAbsent(item.key(), item.price()) != null) {
                throw new IllegalArgumentException(ConsoleText.tr("Item en double dans le catalogue : {0}",
                        item.key()));
            }
        }
        return new ConfigPriceCatalog(prices);
    }

    @Override
    public Optional<BigDecimal> unitPrice(ItemKey item) {
        return Optional.ofNullable(prices.get(item));
    }

    /**
     * Items acceptes.
     *
     * @return les cles, non modifiables
     */
    public Set<ItemKey> items() {
        return prices.keySet();
    }
}
