package dev.novadev.novabank.core.application;

import dev.novadev.novabank.api.entity.EntityMembership;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Banque active d'un joueur et ses autres banques accessibles, pour {@code /bank info}.
 *
 * @param status      issue de la consultation
 * @param active      appartenance active, si le joueur en a une
 * @param memberships toutes ses appartenances
 * @param snapshot    etat de la banque active, si la consultation a abouti
 */
public record BankView(QueryStatus status, Optional<EntityMembership> active, List<EntityMembership> memberships,
                       Optional<BankSnapshot> snapshot) {

    /**
     * @throws NullPointerException si un champ est nul
     */
    public BankView {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(active, "active");
        memberships = List.copyOf(memberships);
        Objects.requireNonNull(snapshot, "snapshot");
    }

    /**
     * @param status motif de l'echec
     * @return une consultation sans resultat
     */
    static BankView failed(QueryStatus status) {
        return new BankView(status, Optional.empty(), List.of(), Optional.empty());
    }
}
