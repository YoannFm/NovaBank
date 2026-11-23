package dev.novadev.novabank.config.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.config.model.AcceptedItem;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ConfigPriceCatalogTest {

    private static AcceptedItem item(String key, String price) {
        return new AcceptedItem(ItemKey.parse(key), new BigDecimal(price), Optional.empty(), Optional.empty());
    }

    @Test
    void prix_exacts_et_item_absent() {
        ConfigPriceCatalog catalog = ConfigPriceCatalog.of(List.of(item("minecraft:stone", "0.1000"),
                item("nexo:ruby", "12")));
        assertThat(catalog.unitPrice(ItemKey.minecraft("stone"))).hasValueSatisfying(
                price -> assertThat(price.toPlainString()).isEqualTo("0.1000"));
        assertThat(catalog.unitPrice(ItemKey.minecraft("dirt"))).isEmpty();
        assertThat(catalog.items()).containsExactlyInAnyOrder(ItemKey.minecraft("stone"), ItemKey.parse("nexo:ruby"));
        assertThatThrownBy(() -> catalog.items().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void doublon_refuse() {
        assertThatThrownBy(() -> ConfigPriceCatalog.of(List.of(item("minecraft:stone", "1"), item("minecraft:stone", "2"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("minecraft:stone");
    }
}
