package dev.novadev.novabank.core.testing;

import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.core.application.InventoryGateway;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Inventaires en memoire. Verifie a chaque appel qu'il a lieu sur le thread du joueur.
 * Une capacite par joueur borne le nombre d'unites qu'il peut encore recevoir.
 */
public final class FakeInventory implements InventoryGateway {

    private final Map<UUID, Map<ItemKey, Long>> contents = new HashMap<>();
    private final Map<UUID, Long> capacity = new HashMap<>();
    private final Map<UUID, ItemKey> held = new HashMap<>();
    private final EventLog log;

    /**
     * @param log journal des evenements
     */
    public FakeInventory(EventLog log) {
        this.log = log;
    }

    /**
     * @param playerId joueur
     * @param item     item
     * @param quantity quantite placee dans l'inventaire
     * @return cet inventaire
     */
    public FakeInventory put(UUID playerId, ItemKey item, long quantity) {
        contents.computeIfAbsent(playerId, id -> new HashMap<>()).merge(item, quantity, Long::sum);
        return this;
    }

    /**
     * @param playerId joueur
     * @param units    unites qu'il peut encore recevoir
     */
    public void capacity(UUID playerId, long units) {
        capacity.put(playerId, units);
    }

    /**
     * @param playerId joueur
     * @param item     item tenu en main, ou {@code null}
     */
    public void hold(UUID playerId, ItemKey item) {
        held.put(playerId, item);
    }

    /**
     * @param playerId joueur
     * @param item     item
     * @return quantite presente (lecture de test, hors contexte joueur)
     */
    public long quantity(UUID playerId, ItemKey item) {
        return contents.getOrDefault(playerId, Map.of()).getOrDefault(item, 0L);
    }

    @Override
    public long count(UUID playerId, ItemKey item) {
        requirePlayerThread();
        return quantity(playerId, item);
    }

    @Override
    public long remove(UUID playerId, ItemKey item, long max) {
        requirePlayerThread();
        long removed = Math.min(max, quantity(playerId, item));
        contents.computeIfAbsent(playerId, id -> new HashMap<>()).merge(item, -removed, Long::sum);
        log.add("inventaire:retrait:" + item + ":" + removed);
        return removed;
    }

    @Override
    public long give(UUID playerId, ItemKey item, long quantity) {
        requirePlayerThread();
        long room = capacity.getOrDefault(playerId, Long.MAX_VALUE);
        long given = Math.min(room, quantity);
        capacity.computeIfPresent(playerId, (id, left) -> left - given);
        put(playerId, item, given);
        log.add("inventaire:don:" + item + ":" + given);
        return quantity - given;
    }

    @Override
    public Optional<ItemKey> heldItem(UUID playerId) {
        requirePlayerThread();
        return Optional.ofNullable(held.get(playerId));
    }

    private static void requirePlayerThread() {
        assertThat(ManualScheduler.context()).as("acces inventaire hors thread joueur")
                .isEqualTo(ManualScheduler.Context.PLAYER);
    }
}
