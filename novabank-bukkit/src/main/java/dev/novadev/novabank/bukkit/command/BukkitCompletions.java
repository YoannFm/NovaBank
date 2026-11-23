package dev.novadev.novabank.bukkit.command;

import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.core.application.TransferRules;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.Server;
import org.bukkit.entity.Player;

/**
 * Donnees de completion : regles en vigueur, stock connu et joueurs connectes.
 */
public final class BukkitCompletions implements CompletionSource {

    private final Supplier<TransferRules> rules;
    private final KnownStock stock;
    private final Server server;

    /**
     * @param rules  regles en vigueur
     * @param stock  stock connu des joueurs
     * @param server serveur
     */
    public BukkitCompletions(Supplier<TransferRules> rules, KnownStock stock, Server server) {
        this.rules = Objects.requireNonNull(rules, "rules");
        this.stock = Objects.requireNonNull(stock, "stock");
        this.server = Objects.requireNonNull(server, "server");
    }

    @Override
    public List<ItemKey> acceptedItems() {
        return rules.get().acceptedItems();
    }

    @Override
    public Collection<ItemKey> knownStock(UUID playerId) {
        return stock.of(playerId);
    }

    @Override
    public List<String> entityTypes() {
        return rules.get().enabledTypes();
    }

    @Override
    public List<String> onlinePlayers() {
        return server.getOnlinePlayers().stream().map(Player::getName).toList();
    }
}
