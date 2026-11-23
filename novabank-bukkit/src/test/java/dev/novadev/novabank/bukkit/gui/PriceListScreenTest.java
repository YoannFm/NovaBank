package dev.novadev.novabank.bukkit.gui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.bukkit.testing.TestLanguages;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class PriceListScreenTest {

    private final List<String> calls = new ArrayList<>();

    private PriceListScreen screen(int count) {
        List<PriceListScreen.Entry> entries = IntStream.range(0, count).mapToObj(i -> new PriceListScreen.Entry(
                ItemKey.minecraft("item_" + i), "Item " + i, BigDecimal.valueOf(i), i == 1 ? "minerais" : null)).toList();
        return new PriceListScreen(TestLanguages::french, entries, () -> calls.add("back"));
    }

    @Test
    void chaque_item_montre_son_prix_et_sa_categorie() {
        PriceListScreen screen = screen(3);

        SlotView paid = screen.slot(1).orElseThrow();
        assertThat(paid.icon()).isEqualTo(ItemKey.minecraft("item_1"));
        assertThat(paid.name()).contains("Item 1");
        assertThat(String.join("|", paid.lore())).contains("1").contains("minerais");
        assertThat(String.join("|", screen.slot(0).orElseThrow().lore())).as("prix nul").contains("Ne rapporte rien");
        assertThat(screen.slot(3)).isEmpty();
    }

    @Test
    void pages_de_45_et_retour_a_la_banque() {
        PriceListScreen screen = screen(50);
        assertThat(screen.slot(PriceListScreen.PREVIOUS)).isEmpty();
        assertThat(screen.slot(PriceListScreen.NEXT)).isPresent();

        screen.click(PriceListScreen.NEXT, MenuClick.LEFT, Optional.empty());
        assertThat(screen.slot(0).orElseThrow().name()).contains("Item 45");
        assertThat(screen.slot(5)).isEmpty();
        assertThat(screen.slot(PriceListScreen.NEXT)).isEmpty();

        screen.click(PriceListScreen.BACK, MenuClick.OTHER, Optional.empty());
        assertThat(calls).as("clic ignore").isEmpty();
        screen.click(PriceListScreen.BACK, MenuClick.LEFT, Optional.empty());
        assertThat(calls).containsExactly("back");
    }
}
