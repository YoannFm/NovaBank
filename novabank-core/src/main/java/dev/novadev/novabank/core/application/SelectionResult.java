package dev.novadev.novabank.core.application;

import dev.novadev.novabank.api.entity.EntityMembership;
import java.util.Objects;
import java.util.Optional;

/**
 * Resultat du choix de la banque active.
 *
 * @param status   issue
 * @param selected appartenance desormais active, si le choix est enregistre
 */
public record SelectionResult(QueryStatus status, Optional<EntityMembership> selected) {

    /**
     * @throws NullPointerException si un champ est nul
     */
    public SelectionResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(selected, "selected");
    }

    static SelectionResult failed(QueryStatus status) {
        return new SelectionResult(status, Optional.empty());
    }
}
