package dev.novadev.novabank.bukkit.gui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.bukkit.config.BukkitItemAvailability;
import dev.novadev.novabank.bukkit.config.ConfigFile;
import dev.novadev.novabank.bukkit.config.ConfigurationSource;
import dev.novadev.novabank.bukkit.config.YamlSections;
import dev.novadev.novabank.bukkit.testing.RecordingLogger;
import dev.novadev.novabank.bukkit.testing.TestConfigs;
import dev.novadev.novabank.bukkit.testing.TestLanguages;
import dev.novadev.novabank.config.ConfigLoadResult;
import dev.novadev.novabank.config.NovaBankConfigLoader;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ItemsEditorTest {

    private static final ItemKey STONE = ItemKey.minecraft("stone");
    private static final ItemKey RUBY = ItemKey.parse("itemsadder:pack/ruby");

    private final List<Map<String, Object>> raw = List.of(
            Map.of("key", "minecraft:stone", "price", "0.5", "category", "pierre"),
            Map.of("key", "itemsadder:pack/ruby", "price", "120", "display-name", "Rubis \"rare\""));

    private ItemsEditor editor() {
        return new ItemsEditor(raw, TestLanguages::french);
    }

    @Test
    void part_de_la_liste_brute_du_fichier_et_n_est_pas_modifie_au_depart() {
        ItemsEditor editor = editor();

        assertThat(editor.dirty()).isFalse();
        assertThat(editor.entries()).extracting(ItemsEditor.Entry::key).containsExactly(STONE, RUBY);
        assertThat(editor.summary()).containsEntry("minecraft:stone", "0.5 [pierre]").containsEntry("itemsadder:pack/ruby", "120");
    }

    @Test
    void ajoute_modifie_et_retire_avec_les_bornes() {
        ItemsEditor editor = editor();
        ItemKey diamond = ItemKey.minecraft("diamond_block");

        assertThat(editor.add(diamond, new BigDecimal("90"), "minerais")).isTrue();
        assertThat(editor.add(diamond, new BigDecimal("-1"), null)).isFalse();
        assertThat(editor.add(diamond, new BigDecimal("1"), "Mauvaise Categorie")).isFalse();
        assertThat(editor.setPrice(STONE, new BigDecimal("2.50"))).isTrue();
        assertThat(editor.setPrice(ItemKey.minecraft("dirt"), BigDecimal.ONE)).isFalse();
        assertThat(editor.setCategory(RUBY, "custom")).isTrue();
        assertThat(editor.setCategory(RUBY, null)).isTrue();
        assertThat(editor.remove(diamond)).isTrue();
        assertThat(editor.remove(diamond)).isFalse();

        assertThat(editor.dirty()).isTrue();
        assertThat(editor.summary()).containsEntry("minecraft:stone", "2.5 [pierre]");
    }

    @Test
    void le_coffre_ajuste_les_prix_retire_et_ajoute_l_item_en_main() {
        ItemsEditor editor = editor();

        editor.click(0, MenuClick.LEFT, Optional.empty());
        editor.click(0, MenuClick.LEFT, Optional.empty());
        editor.click(0, MenuClick.RIGHT, Optional.empty());
        assertThat(editor.summary()).containsEntry("minecraft:stone", "1.5 [pierre]");
        for (int i = 0; i < 5; i++) {
            editor.click(0, MenuClick.RIGHT, Optional.empty()); // jamais sous zero
        }
        assertThat(editor.summary().get("minecraft:stone")).startsWith("0 ");

        editor.click(49, MenuClick.LEFT, Optional.of(ItemKey.minecraft("emerald")));
        editor.click(49, MenuClick.LEFT, Optional.of(ItemKey.minecraft("emerald"))); // deja present : ignore
        assertThat(editor.entries()).hasSize(3);
        editor.click(1, MenuClick.SHIFT, Optional.empty());
        assertThat(editor.summary()).doesNotContainKey("itemsadder:pack/ruby");
        assertThat(editor.slot(0)).hasValueSatisfying(view -> assertThat(view.name()).contains("minecraft:stone"));
        assertThat(editor.slot(3)).isEmpty();
    }

    @Test
    void les_pages_se_tournent_au_dela_de_45_items() {
        List<Map<String, Object>> many = new java.util.ArrayList<>();
        for (int i = 0; i < 50; i++) {
            many.add(Map.of("key", "minecraft:item_" + i, "price", "1"));
        }
        ItemsEditor editor = new ItemsEditor(many, TestLanguages::french);

        assertThat(editor.title()).contains("1/2");
        assertThat(editor.slot(53)).isPresent();
        assertThat(editor.slot(45)).isEmpty();
        editor.click(53, MenuClick.LEFT, Optional.empty());
        assertThat(editor.title()).contains("2/2");
        assertThat(editor.slot(0)).hasValueSatisfying(view -> assertThat(view.name()).contains("item_45"));
        assertThat(editor.slot(45)).isPresent();
    }

    @Test
    void le_bloc_ecrit_conserve_le_nom_avec_guillemets_et_se_relit_a_l_identique(@TempDir Path directory) {
        ItemsEditor editor = editor();
        editor.add(ItemKey.minecraft("gold_block"), new BigDecimal("18"), "minerais");
        String yaml = YamlSections.replace(TestConfigs.defaultYaml(), "items", editor.toBlock());
        ConfigurationSource source = new ConfigurationSource(new ConfigFile(directory.resolve("config.yml"),
                () -> new ByteArrayInputStream(TestConfigs.defaultYaml().getBytes(StandardCharsets.UTF_8))),
                new NovaBankConfigLoader(new BukkitItemAvailability(id -> true, name -> true)), new RecordingLogger().logger());

        ConfigLoadResult result = source.validate(yaml);

        assertThat(result.config()).isPresent();
        assertThat(result.config().get().items()).extracting(item -> item.key().toString())
                .containsExactly("minecraft:stone", "itemsadder:pack/ruby", "minecraft:gold_block");
        assertThat(result.config().get().items().get(1).displayName()).contains("Rubis \"rare\"");
        assertThat(new ItemsEditor((List<?>) result.tree().get("items"), TestLanguages::french).dirty()).isFalse();
    }

    @Test
    void liste_vide_donne_un_bloc_valide() {
        ItemsEditor editor = new ItemsEditor(List.of(), TestLanguages::french);

        assertThat(editor.toBlock()).isEqualTo("items: []");
        assertThat(YamlSections.wellFormed("items", editor.toBlock())).isTrue();
    }
}
