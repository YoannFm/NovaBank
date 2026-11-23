package dev.novadev.novabank.config;

import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.config.catalog.ItemAvailability;
import java.util.Set;

/**
 * Doublure du port de disponibilite : fournisseurs presents et items manquants explicites.
 */
public final class FakeItemAvailability implements ItemAvailability {

    private final Set<String> providers;
    private final Set<ItemKey> missing;

    public FakeItemAvailability(Set<String> providers, Set<ItemKey> missing) {
        this.providers = Set.copyOf(providers);
        this.missing = Set.copyOf(missing);
    }

    /** Tout existe, ItemsAdder, Nexo et Oraxen installes. */
    public static FakeItemAvailability everything() {
        return new FakeItemAvailability(Set.of("itemsadder", "nexo", "oraxen"), Set.of());
    }

    @Override
    public boolean providerPresent(String namespace) {
        return providers.contains(namespace);
    }

    @Override
    public boolean exists(ItemKey key) {
        return !missing.contains(key);
    }
}
