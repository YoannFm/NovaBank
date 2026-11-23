package dev.novadev.novabank.bukkit.gui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.bukkit.testing.TestLanguages;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ItemPriceScreenTest {

    private static final ItemKey STONE = ItemKey.minecraft("stone");

    private final List<String> calls = new ArrayList<>();
    private final ItemsEditor items = new ItemsEditor(List.of(Map.of("key", "minecraft:stone", "price", "10")),
            TestLanguages::french);
    private final ItemPriceScreen screen = new ItemPriceScreen(TestLanguages::french, items, STONE,
            () -> calls.add("back"), field -> calls.add("ask " + field));

    private String price() {
        return items.find(STONE).map(entry -> entry.price().toPlainString()).orElse("absent");
    }

    @Test
    void les_vitres_ajustent_le_prix_sans_passer_sous_zero() {
        screen.click(ItemPriceScreen.PLUS_BIG, MenuClick.LEFT, Optional.empty());
        screen.click(ItemPriceScreen.PLUS_SMALL, MenuClick.LEFT, Optional.empty());
        assertThat(new BigDecimal(price())).isEqualByComparingTo("60.50");
        screen.click(ItemPriceScreen.MINUS_SMALL, MenuClick.LEFT, Optional.empty());
        screen.click(ItemPriceScreen.MINUS_BIG, MenuClick.LEFT, Optional.empty());
        screen.click(ItemPriceScreen.MINUS_BIG, MenuClick.LEFT, Optional.empty());
        assertThat(new BigDecimal(price())).isEqualByComparingTo("0");
    }

    @Test
    void panneau_barriere_et_emeraudes() {
        screen.click(ItemPriceScreen.TYPE, MenuClick.LEFT, Optional.empty());
        screen.click(ItemPriceScreen.BACK, MenuClick.LEFT, Optional.empty());
        assertThat(calls).containsExactly("ask PRICE", "back");
        assertThat(screen.slot(ItemPriceScreen.ITEM)).hasValueSatisfying(view -> assertThat(view.icon()).isEqualTo(STONE));

        screen.click(ItemPriceScreen.DELETE_B, MenuClick.LEFT, Optional.empty());
        assertThat(price()).isEqualTo("absent");
        assertThat(calls).containsExactly("ask PRICE", "back", "back");
    }

    @Test
    void le_clic_sur_un_item_de_la_liste_ouvre_l_ecran_de_prix() {
        List<ItemKey> selected = new ArrayList<>();
        items.onSelect(selected::add);
        items.click(0, MenuClick.LEFT, Optional.empty());
        assertThat(selected).containsExactly(STONE);
        assertThat(price()).isEqualTo("10");
    }

    @Test
    void coffre_et_etiquette_demandent_la_categorie_et_le_nom() {
        screen.click(ItemPriceScreen.CATEGORY, MenuClick.LEFT, Optional.empty());
        screen.click(ItemPriceScreen.NAME, MenuClick.RIGHT, Optional.empty());
        assertThat(calls).containsExactly("ask CATEGORY", "ask NAME");

        items.setCategory(STONE, "pierre");
        items.setDisplayName(STONE, "&7Pierre lisse");
        assertThat(screen.slot(ItemPriceScreen.ITEM)).hasValueSatisfying(view -> {
            assertThat(view.name()).contains("Pierre lisse");
            assertThat(String.join("|", view.lore())).contains("pierre").contains("minecraft:stone");
        });
        assertThat(items.toBlock()).contains("category: pierre").contains("display-name: \"&7Pierre lisse\"");
    }

    @Test
    void nom_vide_refuse_tiret_retire() {
        assertThat(items.setDisplayName(STONE, "  ")).isFalse();
        assertThat(items.setDisplayName(ItemKey.minecraft("dirt"), "Terre")).isFalse();
        assertThat(items.setDisplayName(STONE, "Pierre")).isTrue();
        assertThat(items.setDisplayName(STONE, null)).isTrue();
        assertThat(items.find(STONE)).hasValueSatisfying(entry -> assertThat(entry.displayName()).isNull());
        assertThat(items.dirty()).isFalse();
    }
}
