package dev.novadev.novabank.bukkit.command;

import dev.novadev.novabank.api.item.ItemKey;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Items connus dans la banque active de chaque joueur connecte, pour la completion de
 * {@code withdraw} sans requete en base : rempli par chaque consultation et chaque depot,
 * oublie a la deconnexion. Une entree perimee ne propose qu'un item de trop.
 */
public final class KnownStock {

    private final Map<UUID, Set<ItemKey>> items = new ConcurrentHashMap<>();

    /**
     * @param playerId joueur
     * @param stock    items en stock lus en base (remplacent les precedents)
     */
    public void replace(UUID playerId, Collection<ItemKey> stock) {
        Set<ItemKey> known = ConcurrentHashMap.newKeySet();
        known.addAll(stock);
        items.put(playerId, known);
    }

    /**
     * @param playerId joueur
     * @param item     item depose
     */
    public void add(UUID playerId, ItemKey item) {
        items.computeIfAbsent(playerId, id -> ConcurrentHashMap.newKeySet()).add(item);
    }

    /**
     * @param playerId joueur
     * @return items connus
     */
    public Collection<ItemKey> of(UUID playerId) {
        return Set.copyOf(items.getOrDefault(playerId, Set.of()));
    }

    /**
     * @param playerId joueur deconnecte
     */
    public void forget(UUID playerId) {
        items.remove(playerId);
    }
}
