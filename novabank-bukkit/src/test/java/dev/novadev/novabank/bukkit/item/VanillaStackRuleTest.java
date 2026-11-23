package dev.novadev.novabank.bukkit.item;

import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.bukkit.item.VanillaStackRule.StackFacts;
import org.junit.jupiter.api.Test;

class VanillaStackRuleTest {

    @Test
    void une_pile_vanilla_sans_meta_est_identifiee() {
        assertThat(VanillaStackRule.identify(new StackFacts("minecraft:stone", 64, false, true)))
                .contains(ItemKey.minecraft("stone"));
    }

    @Test
    void une_pile_modifiee_vide_ou_hors_vanilla_est_refusee() {
        assertThat(VanillaStackRule.identify(new StackFacts("minecraft:diamond_sword", 1, false, false))).isEmpty();
        assertThat(VanillaStackRule.identify(new StackFacts("minecraft:air", 1, true, true))).isEmpty();
        assertThat(VanillaStackRule.identify(new StackFacts("minecraft:stone", 0, false, true))).isEmpty();
        assertThat(VanillaStackRule.identify(new StackFacts("autreplugin:bloc", 1, false, true))).isEmpty();
        assertThat(VanillaStackRule.identify(new StackFacts("Pas Une Cle", 1, false, true))).isEmpty();
    }
}
