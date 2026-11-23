package dev.novadev.novabank.core.ownership;

import dev.novadev.novabank.api.entity.BankRole;
import dev.novadev.novabank.api.entity.EntityMembership;
import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.entity.OwnershipProvider;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Fournisseur integre du type {@code player} : chaque joueur est proprietaire de sa
 * banque personnelle.
 */
public final class PlayerOwnershipProvider implements OwnershipProvider {

    private final Function<UUID, Optional<String>> names;

    /**
     * @param names nom connu d'un joueur (plateforme), vide s'il est inconnu
     */
    public PlayerOwnershipProvider(Function<UUID, Optional<String>> names) {
        this.names = Objects.requireNonNull(names, "names");
    }

    @Override
    public String entityType() {
        return EntityRef.PLAYER;
    }

    @Override
    public List<EntityMembership> resolve(UUID playerId) {
        return List.of(new EntityMembership(EntityRef.player(playerId), BankRole.OWNER));
    }

    @Override
    public Optional<String> displayName(EntityRef entity) {
        try {
            return names.apply(UUID.fromString(entity.id()));
        } catch (IllegalArgumentException notAUuid) {
            return Optional.empty();
        }
    }
}
