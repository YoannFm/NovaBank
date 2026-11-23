package dev.novadev.novabank.bukkit.gui;

import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.bukkit.item.ItemProviders;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Place disponible dans l'inventaire principal (36 cases) d'un joueur, lue sur son thread :
 * cases vides et piles identiques non pleines.
 */
final class PlayerInventoryRoom implements InventoryRoom {

    private final Player player;
    private final ItemProviders providers;

    PlayerInventoryRoom(Player player, ItemProviders providers) {
        this.player = Objects.requireNonNull(player, "player");
        this.providers = Objects.requireNonNull(providers, "providers");
    }

    @Override
    public int maxStackSize(ItemKey item) {
        return providers.provider(item).map(provider -> provider.maxStackSize(item)).orElse(1);
    }

    @Override
    public long room(ItemKey item) {
        int max = maxStackSize(item);
        long room = 0L;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack == null || stack.getType().isAir()) {
                room += max;
            } else if (providers.identify(stack).filter(item::equals).isPresent()) {
                room += Math.max(0, max - stack.getAmount());
            }
        }
        return room;
    }
}
