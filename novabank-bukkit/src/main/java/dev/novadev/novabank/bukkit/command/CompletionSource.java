package dev.novadev.novabank.bukkit.command;

import dev.novadev.novabank.api.item.ItemKey;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Donnees de completion des commandes, disponibles sans acces a la base (la completion
 * est synchrone, sur le thread de l'emetteur).
 */
public interface CompletionSource {

    /**
     * @return items acceptes en depot
     */
    List<ItemKey> acceptedItems();

    /**
     * @param playerId joueur
     * @return items connus dans le stock de sa banque active (derniere consultation et depots depuis)
     */
    Collection<ItemKey> knownStock(UUID playerId);

    /**
     * @return types de banque actives
     */
    List<String> entityTypes();

    /**
     * @return noms des joueurs connectes
     */
    List<String> onlinePlayers();
}
