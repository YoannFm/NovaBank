package dev.novadev.novabank.api.entity;

import java.util.Objects;

/**
 * Appartenance d'un joueur a une entite proprietaire de banque, avec son role.
 *
 * @param entity entite (joueur, ile, faction...)
 * @param role   role du joueur dans cette entite
 */
public record EntityMembership(EntityRef entity, BankRole role) {

    /**
     * Construit une appartenance.
     *
     * @throws NullPointerException si une partie est nulle
     */
    public EntityMembership {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(role, "role");
    }
}
