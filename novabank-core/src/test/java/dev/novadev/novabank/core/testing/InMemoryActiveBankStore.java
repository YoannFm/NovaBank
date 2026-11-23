package dev.novadev.novabank.core.testing;

import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.core.selection.ActiveBankStore;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link ActiveBankStore} en memoire.
 */
public final class InMemoryActiveBankStore implements ActiveBankStore {

    private final Map<UUID, EntityRef> choices = new ConcurrentHashMap<>();

    @Override
    public Optional<EntityRef> find(UUID playerId) {
        return Optional.ofNullable(choices.get(playerId));
    }

    @Override
    public void save(UUID playerId, EntityRef entity) {
        choices.put(Objects.requireNonNull(playerId, "playerId"), Objects.requireNonNull(entity, "entity"));
    }

    /**
     * @return copie des choix enregistres (export du contenu)
     */
    public Map<UUID, EntityRef> choices() {
        return Map.copyOf(choices);
    }
}
