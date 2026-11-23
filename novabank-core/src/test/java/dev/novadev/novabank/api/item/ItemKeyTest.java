package dev.novadev.novabank.api.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ItemKeyTest {

    @ParameterizedTest
    @ValueSource(strings = {"minecraft:stone", "itemsadder:pack/ruby_block", "nexo:ruby_block", "my.plugin-2:a.b/c-d_e"})
    void les_cles_valides_se_relisent_a_l_identique(String text) {
        assertThat(ItemKey.parse(text).toString()).isEqualTo(text);
    }

    @Test
    void parse_separe_namespace_et_identifiant() {
        assertThat(ItemKey.parse("itemsadder:pack/ruby_block")).isEqualTo(new ItemKey("itemsadder", "pack/ruby_block"));
        assertThat(ItemKey.minecraft("stone")).isEqualTo(ItemKey.parse("minecraft:stone"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"stone", "Minecraft:stone", "minecraft:Stone", ":stone", "minecraft:", "a:b:c",
            "mine/craft:stone", "minecraft:st one", "minecraft:pierreé"})
    void les_cles_invalides_sont_refusees(String text) {
        assertThatThrownBy(() -> ItemKey.parse(text)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void les_parties_nulles_sont_refusees() {
        assertThatThrownBy(() -> new ItemKey(null, "stone")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ItemKey.parse(null)).isInstanceOf(NullPointerException.class);
    }
}
