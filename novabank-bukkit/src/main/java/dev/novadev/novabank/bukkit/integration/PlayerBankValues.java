package dev.novadev.novabank.bukkit.integration;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * Valeurs des banques d'un joueur, gardees en memoire : les placeholders sont lus sur le thread principal et ne
 * doivent jamais attendre le stockage. Une valeur absente ou perimee lance une relecture en arriere-plan et rend la
 * derniere valeur connue.
 */
public final class PlayerBankValues {

    private record Entry(Map<String, BigDecimal> values, long loadedAt) {
    }

    private final Function<UUID, CompletableFuture<Map<String, BigDecimal>>> loader;
    private final LongSupplier clock;
    private final long ttlMillis;
    private final Map<UUID, Entry> cache = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> loading = new ConcurrentHashMap<>();

    /**
     * @param loader    lit les valeurs des banques d'un joueur, par type d'entite
     * @param clock     horloge en millisecondes
     * @param ttlMillis duree avant relecture
     */
    public PlayerBankValues(Function<UUID, CompletableFuture<Map<String, BigDecimal>>> loader, LongSupplier clock,
                            long ttlMillis) {
        this.loader = loader;
        this.clock = clock;
        this.ttlMillis = ttlMillis;
    }

    /**
     * @param player joueur
     * @param type   type de banque
     * @return la derniere valeur connue, vide si aucune banque de ce type n'a encore ete lue
     */
    public Optional<BigDecimal> value(UUID player, String type) {
        Entry entry = cache.get(player);
        if ((entry == null || clock.getAsLong() - entry.loadedAt() >= ttlMillis) && loading.putIfAbsent(player, true) == null) {
            loader.apply(player).whenComplete((values, failure) -> {
                if (failure == null && values != null) {
                    cache.put(player, new Entry(Map.copyOf(values), clock.getAsLong()));
                }
                loading.remove(player);
            });
        }
        return Optional.ofNullable(entry).map(found -> found.values().get(type));
    }

    /**
     * @param player joueur qui se deconnecte
     */
    public void forget(UUID player) {
        cache.remove(player);
    }
}
