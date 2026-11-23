package dev.novadev.novabank.bukkit.command;

import dev.novadev.novabank.api.item.ItemKey;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Donnees de completion fixes.
 */
final class FakeCompletions implements CompletionSource {

    @Override
    public List<ItemKey> acceptedItems() {
        return List.of(ItemKey.minecraft("stone"), ItemKey.minecraft("diamond_block"),
                ItemKey.parse("itemsadder:pack/ruby"));
    }

    @Override
    public Collection<ItemKey> knownStock(UUID playerId) {
        return List.of(ItemKey.minecraft("dirt"));
    }

    @Override
    public List<String> entityTypes() {
        return List.of("player", "island");
    }

    @Override
    public List<String> onlinePlayers() {
        return List.of("Yo", "Alex");
    }
}
