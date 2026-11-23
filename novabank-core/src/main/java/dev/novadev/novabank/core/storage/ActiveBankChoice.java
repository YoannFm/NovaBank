package dev.novadev.novabank.core.storage;

import dev.novadev.novabank.api.entity.EntityRef;
import java.util.Objects;
import java.util.UUID;

/**
 * Banque active choisie par un joueur, pour la copie entre backends.
 *
 * @param playerId joueur
 * @param entity   entite choisie
 */
public record ActiveBankChoice(UUID playerId, EntityRef entity) {

    /** Construit un choix. */
    public ActiveBankChoice {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(entity, "entity");
    }
}
