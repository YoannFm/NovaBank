package dev.novadev.novabank.bukkit.gui;

import static dev.novadev.novabank.bukkit.gui.MenuFixtures.STONE;
import static dev.novadev.novabank.bukkit.gui.MenuFixtures.room;
import static dev.novadev.novabank.bukkit.gui.MenuFixtures.settings;
import static dev.novadev.novabank.bukkit.gui.MenuFixtures.snapshot;
import static dev.novadev.novabank.bukkit.gui.MenuFixtures.stoneBank;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ClickResolverTest {

    private final MenuLayout layout = new MenuLayout(settings(6));

    private MenuAction click(StockPage page, int slot, MenuClick click, long room, long max) {
        return ClickResolver.resolve(layout, page, slot, click, room(room), max);
    }

    @Test
    void gauche_une_pile_droit_une_unite_majuscule_tout_ce_qui_tient() {
        StockPage page = StockPage.of(stoneBank(500), 0, layout.pageSize());

        assertThat(click(page, 0, MenuClick.LEFT, 1000, 2304)).isEqualTo(new MenuAction.Withdraw(STONE, 64));
        assertThat(click(page, 0, MenuClick.RIGHT, 1000, 2304)).isEqualTo(new MenuAction.Withdraw(STONE, 1));
        assertThat(click(page, 0, MenuClick.SHIFT, 300, 2304)).isEqualTo(new MenuAction.Withdraw(STONE, 300));
    }

    @Test
    void quantites_bornees_par_le_stock_et_la_limite_par_operation() {
        StockPage small = StockPage.of(stoneBank(10), 0, layout.pageSize());
        StockPage big = StockPage.of(stoneBank(5000), 0, layout.pageSize());

        assertThat(click(small, 0, MenuClick.LEFT, 1000, 2304)).isEqualTo(new MenuAction.Withdraw(STONE, 10));
        assertThat(click(small, 0, MenuClick.SHIFT, 1000, 2304)).isEqualTo(new MenuAction.Withdraw(STONE, 10));
        assertThat(click(big, 0, MenuClick.SHIFT, 2304, 100)).isEqualTo(new MenuAction.Withdraw(STONE, 100));
        assertThat(click(big, 0, MenuClick.LEFT, 2304, 32)).isEqualTo(new MenuAction.Withdraw(STONE, 32));
    }

    @Test
    void inventaire_plein_aucun_clic_ne_retire() {
        StockPage page = StockPage.of(stoneBank(10), 0, layout.pageSize());

        // Avant le correctif, gauche et droit retiraient quand meme : stock debite, items en livraison differee.
        for (MenuClick kind : List.of(MenuClick.LEFT, MenuClick.RIGHT, MenuClick.SHIFT)) {
            assertThat(click(page, 0, kind, 0, 2304)).as(kind.name()).isEqualTo(new MenuAction.NoRoom(STONE));
        }
    }

    @Test
    void une_pile_bornee_par_la_place_libre() {
        StockPage page = StockPage.of(stoneBank(500), 0, layout.pageSize());

        assertThat(click(page, 0, MenuClick.LEFT, 10, 2304)).isEqualTo(new MenuAction.Withdraw(STONE, 10));
    }

    @Test
    void autres_clics_cases_vides_et_hors_coffre_sans_action() {
        StockPage page = StockPage.of(stoneBank(10), 0, layout.pageSize());

        assertThat(click(page, 0, MenuClick.OTHER, 1000, 2304)).isEqualTo(MenuAction.NONE);
        assertThat(click(page, 1, MenuClick.LEFT, 1000, 2304)).isEqualTo(MenuAction.NONE);
        assertThat(click(page, 46, MenuClick.LEFT, 1000, 2304)).isEqualTo(MenuAction.NONE);
        assertThat(click(page, 60, MenuClick.LEFT, 1000, 2304)).as("inventaire du joueur").isEqualTo(MenuAction.NONE);
    }

    @Test
    void boutons_pages_deposer_et_fermer() {
        StockPage first = StockPage.of(snapshot(100), 0, layout.pageSize());
        StockPage second = StockPage.of(snapshot(100), 1, layout.pageSize());
        StockPage last = StockPage.of(snapshot(100), 2, layout.pageSize());

        assertThat(click(first, 48, MenuClick.LEFT, 0, 1)).as("pas de page precedente").isEqualTo(MenuAction.NONE);
        assertThat(click(first, 50, MenuClick.RIGHT, 0, 1)).isEqualTo(new MenuAction.ShowPage(1));
        assertThat(click(second, 48, MenuClick.SHIFT, 0, 1)).isEqualTo(new MenuAction.ShowPage(0));
        assertThat(click(last, 50, MenuClick.LEFT, 0, 1)).as("pas de page suivante").isEqualTo(MenuAction.NONE);
        assertThat(click(first, 49, MenuClick.LEFT, 0, 1)).isEqualTo(new MenuAction.DepositAll());
        assertThat(click(first, 53, MenuClick.LEFT, 0, 1)).isEqualTo(new MenuAction.Close());
        assertThat(click(first, 53, MenuClick.OTHER, 0, 1)).isEqualTo(MenuAction.NONE);
        assertThat(click(first, 45, MenuClick.LEFT, 0, 1)).as("livre").isEqualTo(new MenuAction.ShowPrices());
    }

    @Test
    void retrait_d_une_quantite_nulle_impossible() {
        assertThatThrownBy(() -> new MenuAction.Withdraw(STONE, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
