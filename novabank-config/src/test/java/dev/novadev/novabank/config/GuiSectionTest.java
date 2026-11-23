package dev.novadev.novabank.config;

import static dev.novadev.novabank.config.TestConfigs.loadValid;
import static dev.novadev.novabank.config.TestConfigs.minimal;
import static dev.novadev.novabank.config.TestConfigs.minimalWith;
import static dev.novadev.novabank.config.TestConfigs.singleError;
import static dev.novadev.novabank.config.TestConfigs.warningPaths;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.config.model.GuiButton;
import dev.novadev.novabank.config.model.GuiButtonSettings;
import dev.novadev.novabank.config.model.GuiSettings;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Section {@code gui}.
 */
class GuiSectionTest {

    @Test
    void valeurs_par_defaut_six_lignes_cinq_boutons_et_remplissage() {
        GuiSettings gui = loadValid(minimal()).gui();

        assertThat(gui.rows()).isEqualTo(6);
        assertThat(gui.buttons()).containsEntry(GuiButton.INFO, new GuiButtonSettings(45, ItemKey.minecraft("book")))
                .containsEntry(GuiButton.CLOSE, new GuiButtonSettings(53, ItemKey.minecraft("barrier")))
                .hasSize(GuiButton.values().length);
        assertThat(gui.filler()).contains(ItemKey.minecraft("gray_stained_glass_pane"));
    }

    @Test
    void lignes_colonnes_icones_et_remplissage_configurables() {
        Map<String, Object> tree = TestConfigs.with(minimalWith("gui.rows", 3), "gui.buttons.next.column", 7);
        tree = TestConfigs.with(tree, "gui.buttons.next.icon", "minecraft:spectral_arrow");
        tree = TestConfigs.with(tree, "gui.filler", "");

        GuiSettings gui = loadValid(tree).gui();

        assertThat(gui.rows()).isEqualTo(3);
        assertThat(gui.buttons().get(GuiButton.NEXT))
                .isEqualTo(new GuiButtonSettings(25, ItemKey.minecraft("spectral_arrow"))); // ancienne cle "column" : derniere ligne
        assertThat(gui.filler()).isEmpty();
    }

    @Test
    void lignes_hors_bornes_et_colonnes_en_double_refusees() {
        singleError(minimalWith("gui.rows", 1), "gui.rows");
        singleError(minimalWith("gui.rows", 7), "gui.rows");
        singleError(minimalWith("gui.buttons.close.column", 9), "gui.buttons.close.column");
        singleError(minimalWith("gui.buttons.next.column", 0), "gui.buttons.next.column");
    }

    @Test
    void case_absolue_sur_n_importe_quelle_ligne_et_cases_invalides_refusees() {
        Map<String, Object> tree = TestConfigs.with(minimalWith("gui.rows", 4), "gui.buttons.close.slot", 2);

        assertThat(loadValid(tree).gui().buttons().get(GuiButton.CLOSE).slot()).isEqualTo(2);
        singleError(TestConfigs.with(minimalWith("gui.rows", 4), "gui.buttons.close.slot", 36), "gui.buttons.close.slot");
        singleError(TestConfigs.with(minimalWith("gui.rows", 4), "gui.buttons.close.slot", 27 + 4),
                "gui.buttons.close.slot"); // 31 = case de deposit-all (colonne 4 de la derniere ligne)
    }

    @Test
    void icone_invalide_ou_non_vanilla_refusee() {
        singleError(minimalWith("gui.buttons.info.icon", "pas une cle"), "gui.buttons.info.icon");
        singleError(minimalWith("gui.buttons.info.icon", "itemsadder:pack/ruby"), "gui.buttons.info.icon");
    }

    @Test
    void icone_inconnue_de_la_version_remplacee_par_le_defaut_avec_avertissement() {
        ItemKey missing = ItemKey.minecraft("cherry_sign");
        NovaBankConfigLoader loader = new NovaBankConfigLoader(new FakeItemAvailability(Set.of(), Set.of(missing)));
        Map<String, Object> tree = TestConfigs.with(minimalWith("gui.buttons.info.icon", missing.toString()),
                "gui.filler", missing.toString());

        ConfigLoadResult result = loader.load(tree);

        assertThat(warningPaths(result)).containsExactly("gui.buttons.info.icon", "gui.filler");
        GuiSettings gui = result.config().orElseThrow().gui();
        assertThat(gui.buttons().get(GuiButton.INFO).icon()).isEqualTo(ItemKey.minecraft("book"));
        assertThat(gui.filler()).contains(ItemKey.minecraft("gray_stained_glass_pane"));
    }

    @Test
    void bouton_inconnu_signale_comme_cle_inconnue() {
        ConfigLoadResult result = TestConfigs.load(minimalWith("gui.buttons.teleport.column", 2));

        assertThat(result.config()).isPresent();
        assertThat(warningPaths(result)).containsExactly("gui.buttons.teleport");
    }

    @Test
    void modele_valide_ses_bornes() {
        Map<GuiButton, GuiButtonSettings> one = Map.of(GuiButton.INFO, new GuiButtonSettings(0, ItemKey.minecraft("book")));
        assertThatThrownBy(() -> new GuiSettings(1, Map.of(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GuiSettings(6, one, Optional.empty())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GuiButtonSettings(54, ItemKey.minecraft("book")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(GuiButton.byKey("deposit-all")).contains(GuiButton.DEPOSIT_ALL);
        assertThat(GuiButton.byKey("teleport")).isEmpty();
    }
}
