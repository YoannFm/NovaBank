package dev.novadev.novabank.config.tree;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConfigTreesTest {

    private static final Map<String, Object> TREE = Map.of("a", Map.of("b", 1), "l", List.of(Map.of("x", 1)));

    @Test
    void copie_non_modifiable_en_profondeur() {
        Map<String, Object> copy = ConfigTrees.immutableCopy(TREE);
        assertThat(copy).isEqualTo(TREE);
        assertThatThrownBy(() -> copy.put("c", 1)).isInstanceOf(UnsupportedOperationException.class);
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) copy.get("l");
        assertThatThrownBy(() -> list.add(1)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void copie_modifiable_en_profondeur() {
        Map<String, Object> copy = ConfigTrees.mutableCopy(TREE);
        @SuppressWarnings("unchecked")
        Map<String, Object> a = (Map<String, Object>) copy.get("a");
        a.put("c", 2);
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) copy.get("l");
        list.add(3);
        assertThat(list).isInstanceOf(ArrayList.class).hasSize(2);
        assertThat(TREE.get("a")).isEqualTo(Map.of("b", 1));
    }

    @Test
    void valeur_posee_avec_creation_des_sections_et_source_inchangee() {
        Map<String, Object> edited = ConfigTrees.withValue(TREE, "x.y.z", 5).orElseThrow();
        assertThat(edited).containsEntry("x", Map.of("y", Map.of("z", 5))).containsEntry("a", Map.of("b", 1));
        assertThat(ConfigTrees.withValue(TREE, "a.b", 2).orElseThrow()).containsEntry("a", Map.of("b", 2));
        assertThat(TREE).doesNotContainKey("x");
    }

    @Test
    void valeur_nulle_retire_la_cle() {
        assertThat(ConfigTrees.withValue(TREE, "a.b", null).orElseThrow()).containsEntry("a", Map.of());
    }

    @Test
    void niveau_intermediaire_non_section_refuse() {
        assertThat(ConfigTrees.withValue(TREE, "a.b.c", 1)).isEmpty();
        assertThat(ConfigTrees.withValue(TREE, "l.x", 1)).isEmpty();
    }

    @Test
    void chemins_construits() {
        assertThat(ConfigTrees.child("", "a")).isEqualTo("a");
        assertThat(ConfigTrees.child("a", "b")).isEqualTo("a.b");
        assertThat(ConfigTrees.element("items", 2)).isEqualTo("items[2]");
    }
}
