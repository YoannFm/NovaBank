package dev.novadev.novabank.bukkit.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.novadev.novabank.api.item.ItemKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class ItemProvidersTest {

    private final List<String> asked = new ArrayList<>();

    @Test
    void les_fournisseurs_custom_sont_interroges_avant_le_vanilla() {
        ItemProviders providers = new ItemProviders();
        providers.register(provider("minecraft", Optional.of(ItemKey.minecraft("stone"))));
        providers.register(provider("nexo", Optional.empty()));

        assertThat(providers.identify(new ItemStack(Material.STONE, 3))).contains(ItemKey.minecraft("stone"));
        assertThat(asked).containsExactly("nexo", "minecraft");
        assertThat(providers.provider(ItemKey.parse("nexo:ruby"))).isPresent();
        assertThat(providers.provider(ItemKey.parse("oraxen:ruby"))).isEmpty();
    }

    @Test
    void pile_absente_ou_vide_jamais_identifiee_et_namespace_unique() {
        ItemProviders providers = new ItemProviders();
        providers.register(provider("minecraft", Optional.of(ItemKey.minecraft("stone"))));

        assertThat(providers.identify(null)).isEmpty();
        assertThat(providers.identify(new ItemStack(Material.STONE, 0))).isEmpty();
        assertThat(asked).isEmpty();
        assertThatThrownBy(() -> providers.register(provider("minecraft", Optional.empty())))
                .isInstanceOf(IllegalStateException.class);
    }

    private ItemProvider provider(String namespace, Optional<ItemKey> answer) {
        return new ItemProvider() {
            @Override
            public String namespace() {
                return namespace;
            }

            @Override
            public Optional<ItemKey> identify(ItemStack stack) {
                asked.add(namespace);
                return answer;
            }

            @Override
            public Optional<ItemStack> create(ItemKey key, int amount) {
                return Optional.empty();
            }

            @Override
            public int maxStackSize(ItemKey key) {
                return 64;
            }
        };
    }
}
