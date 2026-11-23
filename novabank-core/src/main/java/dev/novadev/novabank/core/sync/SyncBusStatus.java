package dev.novadev.novabank.core.sync;

import java.util.Objects;

/**
 * Etat d'une liaison de bus, pour le diagnostic.
 *
 * @param connected   {@code true} si la liaison d'abonnement et la liaison de publication sont etablies
 * @param description cible lisible sans secret (hote, port, base)
 * @param lastError   derniere erreur (sans secret), vide si aucune
 */
public record SyncBusStatus(boolean connected, String description, String lastError) {

    /**
     * @throws NullPointerException si un champ est nul
     */
    public SyncBusStatus {
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(lastError, "lastError");
    }
}
