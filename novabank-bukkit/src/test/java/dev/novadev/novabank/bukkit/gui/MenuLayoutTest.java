package dev.novadev.novabank.bukkit.gui;

import static dev.novadev.novabank.bukkit.gui.MenuFixtures.settings;
import static dev.novadev.novabank.bukkit.gui.MenuFixtures.snapshot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.novadev.novabank.config.model.GuiButton;
import org.junit.jupiter.api.Test;

class MenuLayoutTest {

    @Test
    void stock_sur_les_premieres_lignes_boutons_sur_la_derniere() {
        MenuLayout layout = new MenuLayout(settings(6));

        assertThat(layout.size()).isEqualTo(54);
        assertThat(layout.pageSize()).isEqualTo(45);
        assertThat(layout.stockIndex(0)).contains(0);
        assertThat(layout.stockIndex(44)).contains(44);
        assertThat(layout.stockIndex(45)).isEmpty();
        assertThat(layout.stockIndex(-1)).isEmpty();
        assertThat(layout.slot(GuiButton.INFO)).isEqualTo(45);
        assertThat(layout.slot(GuiButton.CLOSE)).isEqualTo(53);
        assertThat(layout.button(49)).contains(GuiButton.DEPOSIT_ALL);
        assertThat(layout.button(46)).isEmpty();
        assertThat(layout.fillerSlots()).containsExactly(46, 47, 51, 52);
    }

    @Test
    void bouton_place_dans_la_zone_de_stock_decale_le_stock() {
        java.util.Map<GuiButton, dev.novadev.novabank.config.model.GuiButtonSettings> buttons =
                new java.util.EnumMap<>(settings(3).buttons());
        buttons.put(GuiButton.CLOSE, new dev.novadev.novabank.config.model.GuiButtonSettings(4,
                dev.novadev.novabank.api.item.ItemKey.minecraft("barrier")));
        MenuLayout layout = new MenuLayout(new dev.novadev.novabank.config.model.GuiSettings(3, buttons,
                java.util.Optional.empty()));

        assertThat(layout.slot(GuiButton.CLOSE)).isEqualTo(4);
        assertThat(layout.button(4)).contains(GuiButton.CLOSE);
        assertThat(layout.pageSize()).isEqualTo(17); // 18 cases hors barre moins le bouton
        assertThat(layout.stockIndex(4)).isEmpty();
        assertThat(layout.stockIndex(5)).contains(4);
        assertThat(layout.stockSlot(4)).isEqualTo(5);
        assertThat(layout.fillerSlots()).contains(26).doesNotContain(4); // la case 26 (ex-bouton close) est libre
    }

    @Test
    void deux_lignes_une_page_de_neuf() {
        MenuLayout layout = new MenuLayout(settings(2));

        assertThat(layout.size()).isEqualTo(18);
        assertThat(layout.pageSize()).isEqualTo(9);
        assertThat(layout.slot(GuiButton.NEXT)).isEqualTo(14);
    }

    @Test
    void pagination_bornee_et_derniere_page_partielle() {
        StockPage first = StockPage.of(snapshot(20), 0, 9);
        StockPage last = StockPage.of(snapshot(20), 2, 9);
        StockPage beyond = StockPage.of(snapshot(20), 7, 9);
        StockPage negative = StockPage.of(snapshot(20), -3, 9);

        assertThat(first.entries()).hasSize(9);
        assertThat(first.hasPrevious()).isFalse();
        assertThat(first.hasNext()).isTrue();
        assertThat(first.pageCount()).isEqualTo(3);
        assertThat(last.entries()).hasSize(2);
        assertThat(last.entries().get(1).quantity()).isEqualTo(20);
        assertThat(last.hasNext()).isFalse();
        assertThat(beyond.page()).isEqualTo(2);
        assertThat(negative.page()).isZero();
        assertThat(first.at(8)).isPresent();
        assertThat(first.at(9)).isEmpty();
        assertThat(first.at(-1)).isEmpty();
    }

    @Test
    void banque_vide_une_page_vide_et_taille_invalide_refusee() {
        StockPage empty = StockPage.of(snapshot(0), 3, 9);

        assertThat(empty.entries()).isEmpty();
        assertThat(empty.page()).isZero();
        assertThat(empty.pageCount()).isEqualTo(1);
        assertThat(empty.hasNext()).isFalse();
        assertThatThrownBy(() -> StockPage.of(snapshot(1), 0, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void valeur_de_ligne_reprise_du_releve() {
        StockEntry entry = StockPage.of(snapshot(3), 0, 9).entries().get(2);

        assertThat(entry.quantity()).isEqualTo(3);
        assertThat(entry.value()).isEqualByComparingTo("3");
    }
}
