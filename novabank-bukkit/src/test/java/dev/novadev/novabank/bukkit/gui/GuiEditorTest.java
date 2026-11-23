package dev.novadev.novabank.bukkit.gui;

import static dev.novadev.novabank.bukkit.gui.MenuFixtures.settings;
import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.bukkit.config.BukkitItemAvailability;
import dev.novadev.novabank.bukkit.config.ConfigFile;
import dev.novadev.novabank.bukkit.config.ConfigurationSource;
import dev.novadev.novabank.bukkit.config.YamlSections;
import dev.novadev.novabank.bukkit.testing.RecordingLogger;
import dev.novadev.novabank.bukkit.testing.TestConfigs;
import dev.novadev.novabank.bukkit.testing.TestLanguages;
import dev.novadev.novabank.config.NovaBankConfigLoader;
import dev.novadev.novabank.config.model.GuiButton;
import dev.novadev.novabank.config.model.GuiSettings;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GuiEditorTest {

    private final GuiEditor editor = new GuiEditor(settings(6), TestLanguages::french);

    @Test
    void affiche_les_boutons_la_zone_de_stock_et_le_remplissage() {
        assertThat(editor.size()).isEqualTo(54);
        assertThat(editor.dirty()).isFalse();
        assertThat(editor.slot(49)).hasValueSatisfying(view -> {
            assertThat(view.icon()).isEqualTo(ItemKey.minecraft("hopper"));
            assertThat(view.name()).contains("Tout deposer");
        });
        assertThat(editor.slot(10)).hasValueSatisfying(view -> assertThat(view.name()).contains("stock"));
        assertThat(editor.slot(46)).isEmpty(); // settings() n'a pas de remplissage
    }

    @Test
    void prendre_un_bouton_puis_le_poser_le_deplace_et_rend_le_brouillon_different() {
        editor.click(49, MenuClick.LEFT, Optional.empty());
        assertThat(editor.slot(49)).hasValueSatisfying(view -> assertThat(view.name()).contains("en main"));
        assertThat(editor.slot(46)).hasValueSatisfying(view -> assertThat(view.name()).contains("Case libre"));

        editor.click(4, MenuClick.LEFT, Optional.empty()); // dans la zone de stock : autorise

        assertThat(editor.dirty()).isTrue();
        assertThat(editor.slot(4)).hasValueSatisfying(view -> assertThat(view.icon()).isEqualTo(ItemKey.minecraft("hopper")));
        assertThat(editor.slot(49)).isEmpty();
        assertThat(editor.toBlock()).contains("deposit-all:\n      slot: 4\n");
    }

    @Test
    void poser_sur_un_autre_bouton_les_echange_et_recliquer_le_meme_annule() {
        editor.click(45, MenuClick.LEFT, Optional.empty());
        editor.click(53, MenuClick.LEFT, Optional.empty());

        assertThat(editor.toBlock()).contains("info:\n      slot: 53\n").contains("close:\n      slot: 45\n");

        editor.click(49, MenuClick.LEFT, Optional.empty());
        editor.click(49, MenuClick.LEFT, Optional.empty());
        assertThat(editor.slot(49)).hasValueSatisfying(view -> assertThat(view.name()).doesNotContain("en main"));
    }

    @Test
    void clic_droit_prend_l_icone_de_l_objet_en_main_s_il_est_vanilla() {
        editor.click(53, MenuClick.RIGHT, Optional.of(ItemKey.minecraft("emerald")));
        editor.click(45, MenuClick.RIGHT, Optional.of(ItemKey.parse("itemsadder:pack/ruby")));
        editor.click(53, MenuClick.SHIFT, Optional.of(ItemKey.minecraft("gold_ingot")));

        assertThat(editor.toBlock()).contains("icon: minecraft:emerald").contains("icon: minecraft:book");
    }

    @Test
    void changer_les_lignes_replace_les_boutons_et_refuse_les_bornes() {
        assertThat(editor.setRows(7)).isFalse();
        assertThat(editor.setRows(1)).isFalse();

        assertThat(editor.setRows(3)).isTrue();

        assertThat(editor.size()).isEqualTo(27);
        assertThat(editor.toBlock()).doesNotContain("slot: 5"); // plus aucun slot de l'ancienne derniere ligne
        editor.reset();
        assertThat(editor.toBlock()).contains("info:\n      slot: 18\n").contains("close:\n      slot: 26\n");
    }

    @Test
    void le_bloc_ecrit_est_relu_et_valide_a_l_identique(@TempDir Path directory) {
        editor.click(49, MenuClick.LEFT, Optional.empty());
        editor.click(4, MenuClick.LEFT, Optional.empty());
        editor.click(53, MenuClick.RIGHT, Optional.of(ItemKey.minecraft("emerald")));
        editor.setRows(5);
        String yaml = YamlSections.replace(TestConfigs.defaultYaml(), "gui", editor.toBlock());
        ConfigurationSource source = new ConfigurationSource(new ConfigFile(directory.resolve("config.yml"),
                () -> new ByteArrayInputStream(TestConfigs.defaultYaml().getBytes(StandardCharsets.UTF_8))),
                new NovaBankConfigLoader(new BukkitItemAvailability(id -> true, name -> false)), new RecordingLogger().logger());

        GuiSettings loaded = source.validate(yaml).config().orElseThrow().gui();

        assertThat(loaded.rows()).isEqualTo(5);
        assertThat(loaded.buttons().get(GuiButton.DEPOSIT_ALL).slot()).isEqualTo(4);
        assertThat(loaded.buttons().get(GuiButton.CLOSE).icon()).isEqualTo(ItemKey.minecraft("emerald"));
        assertThat(new GuiEditor(loaded, TestLanguages::french).dirty()).isFalse();
    }
}
