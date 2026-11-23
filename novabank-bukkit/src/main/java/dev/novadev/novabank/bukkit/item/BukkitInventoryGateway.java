package dev.novadev.novabank.bukkit.item;

import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.core.application.InventoryGateway;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Inventaire principal d'un joueur connecte (36 cases : barre rapide et inventaire ;
 * armure et seconde main exclues). A appeler sur le thread du joueur uniquement.
 *
 * <p>Seules les piles identifiees par les {@link ItemProviders} comptent : une pile
 * modifiee n'est ni comptee ni retiree.
 */
public final class BukkitInventoryGateway implements InventoryGateway {

    private final Function<UUID, Player> players;
    private final ItemProviders providers;

    /**
     * @param players  joueur connecte par UUID ({@code null} s'il est absent)
     * @param providers fournisseurs d'items
     */
    public BukkitInventoryGateway(Function<UUID, Player> players, ItemProviders providers) {
        this.players = Objects.requireNonNull(players, "players");
        this.providers = Objects.requireNonNull(providers, "providers");
    }

    @Override
    public long count(UUID playerId, ItemKey item) {
        long total = 0L;
        for (ItemStack stack : storage(playerId)) {
            if (matches(stack, item)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    @Override
    public long remove(UUID playerId, ItemKey item, long max) {
        Player player = players.apply(playerId);
        if (player == null || max <= 0) {
            return 0L;
        }
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getStorageContents();
        long removed = 0L;
        for (int slot = 0; slot < contents.length && removed < max; slot++) {
            ItemStack stack = contents[slot];
            if (matches(stack, item)) {
                int taken = (int) Math.min(stack.getAmount(), max - removed);
                removed += taken;
                if (taken == stack.getAmount()) {
                    contents[slot] = null;
                } else {
                    stack.setAmount(stack.getAmount() - taken);
                }
            }
        }
        inventory.setStorageContents(contents);
        return removed;
    }

    @Override
    public long give(UUID playerId, ItemKey item, long quantity) {
        Player player = players.apply(playerId);
        Optional<ItemProvider> provider = providers.provider(item);
        if (player == null || provider.isEmpty() || quantity <= 0) {
            return Math.max(0L, quantity);
        }
        int maxStack = Math.max(1, provider.get().maxStackSize(item));
        long remaining = quantity;
        while (remaining > 0) {
            int amount = (int) Math.min(remaining, maxStack);
            Optional<ItemStack> stack = provider.get().create(item, amount);
            if (stack.isEmpty()) {
                break;
            }
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack.get());
            int notAdded = leftover.values().stream().mapToInt(ItemStack::getAmount).sum();
            remaining -= amount - notAdded;
            if (notAdded > 0) {
                break;
            }
        }
        return remaining;
    }

    @Override
    public Optional<ItemKey> heldItem(UUID playerId) {
        Player player = players.apply(playerId);
        return player == null ? Optional.empty() : providers.identify(player.getInventory().getItemInMainHand());
    }

    private ItemStack[] storage(UUID playerId) {
        Player player = players.apply(playerId);
        return player == null ? new ItemStack[0] : player.getInventory().getStorageContents();
    }

    private boolean matches(ItemStack stack, ItemKey item) {
        return stack != null && providers.identify(stack).filter(item::equals).isPresent();
    }
}
