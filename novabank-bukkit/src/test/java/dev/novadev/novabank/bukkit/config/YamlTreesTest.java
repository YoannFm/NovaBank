package dev.novadev.novabank.bukkit.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.bukkit.testing.TestConfigs;
import dev.novadev.novabank.config.ConfigLoadResult;
import dev.novadev.novabank.config.NovaBankConfigLoader;
import dev.novadev.novabank.config.catalog.ItemAvailability;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class YamlTreesTest {

    private static final ItemAvailability ALL_ITEMS = new ItemAvailability() {
        @Override
        public boolean providerPresent(String namespace) {
            return true;
        }

        @Override
        public boolean exists(ItemKey key) {
            return true;
        }
    };

    @Test
    void sections_imbriquees_en_maps_listes_et_scalaires_conserves() throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                a:
                  b:
                    c: 1
                  flag: true
                  text: "0.10"
                list:
                  - x
                  - y
                objects:
                  - key: minecraft:stone
                    price: "1.5"
                    nested:
                      deep: [1, 2]
                empty: {}
                """);

        Map<String, Object> tree = YamlTrees.toMap(yaml);

        assertThat(tree).containsOnlyKeys("a", "list", "objects", "empty");
        assertThat(tree.get("a")).isEqualTo(Map.of("b", Map.of("c", 1), "flag", true, "text", "0.10"));
        assertThat(tree.get("list")).isEqualTo(List.of("x", "y"));
        assertThat(tree.get("objects")).isEqualTo(List.of(
                Map.of("key", "minecraft:stone", "price", "1.5", "nested", Map.of("deep", List.of(1, 2)))));
        assertThat(tree.get("empty")).isEqualTo(Map.of());
        assertThat(List.copyOf(tree.keySet())).containsExactly("a", "list", "objects", "empty");
    }

    @Test
    void l_arbre_produit_est_non_modifiable() throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("a:\n  b: 1\nl: [1]\n");
        Map<String, Object> tree = YamlTrees.toMap(yaml);

        assertThatThrownBy(() -> tree.put("x", 1)).isInstanceOf(UnsupportedOperationException.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) tree.get("a");
        assertThatThrownBy(() -> nested.put("x", 1)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void le_config_yml_par_defaut_lu_par_bukkit_est_accepte_par_le_chargeur() throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(TestConfigs.defaultYaml());

        ConfigLoadResult result = new NovaBankConfigLoader(ALL_ITEMS).load(YamlTrees.toMap(yaml));

        assertThat(result.report().issues()).isEmpty();
        assertThat(result.config()).hasValueSatisfying(config -> {
            assertThat(config.items()).hasSize(5);
            assertThat(config.items().get(0).price()).isEqualByComparingTo("0.1");
        });
    }
}
