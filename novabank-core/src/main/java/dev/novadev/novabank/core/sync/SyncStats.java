package dev.novadev.novabank.core.sync;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Compteurs de la synchronisation, pour le diagnostic.
 *
 * @param serverId         identifiant de ce serveur
 * @param published        evenements acceptes par le bus
 * @param publishRefused   evenements refuses (bus coupe) : rattrapes par les autres serveurs a leur lecture
 * @param fromBus          changements distants delivres grace au bus
 * @param fromStorage      changements distants delivres grace a la lecture du stockage
 * @param lastPoll         fin de la derniere lecture reussie, vide si aucune
 * @param lastPollError    derniere erreur de lecture (sans secret), vide si la derniere lecture a reussi
 */
public record SyncStats(String serverId, long published, long publishRefused, long fromBus, long fromStorage,
                        Optional<Instant> lastPoll, Optional<String> lastPollError) {

    /**
     * @throws NullPointerException si un champ est nul
     */
    public SyncStats {
        Objects.requireNonNull(serverId, "serverId");
        Objects.requireNonNull(lastPoll, "lastPoll");
        Objects.requireNonNull(lastPollError, "lastPollError");
    }
}
