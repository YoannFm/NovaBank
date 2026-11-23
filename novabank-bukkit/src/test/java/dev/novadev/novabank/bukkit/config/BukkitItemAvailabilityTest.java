package dev.novadev.novabank.bukkit.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.api.item.ItemKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BukkitItemAvailabilityTest {

    private final List<String> vanillaQueries = new ArrayList<>();
    private final BukkitItemAvailability availability = new BukkitItemAvailability(
            id -> {
                vanillaQueries.add(id);
                return Set.of("minecraft:stone", "minecraft:copper_block").contains(id);
            },
            Set.of("ItemsAdder")::contains);

    @Test
    void un_item_vanilla_est_resolu_par_sa_cle_complete() {
        assertThat(availability.exists(ItemKey.minecraft("stone"))).isTrue();
        assertThat(availability.exists(ItemKey.minecraft("cherry_planks"))).isFalse();
        assertThat(vanillaQueries).containsExactly("minecraft:stone", "minecraft:cherry_planks");
    }

    @Test
    void un_item_custom_depend_de_l_activation_du_plugin_fournisseur() {
        assertThat(availability.exists(ItemKey.parse("itemsadder:pack/ruby_block"))).isTrue();
        assertThat(availability.exists(ItemKey.parse("nexo:ruby_block"))).isFalse();
        assertThat(availability.providerPresent("itemsadder")).isTrue();
        assertThat(availability.providerPresent("oraxen")).isFalse();
        assertThat(vanillaQueries).isEmpty();
    }

    @Test
    void un_namespace_inconnu_n_est_jamais_disponible() {
        assertThat(availability.providerPresent("inconnu")).isFalse();
        assertThat(BukkitItemAvailability.providerPlugin("inconnu")).isEmpty();
        assertThat(BukkitItemAvailability.providerPlugin("Nexo")).contains("Nexo");
    }
}
