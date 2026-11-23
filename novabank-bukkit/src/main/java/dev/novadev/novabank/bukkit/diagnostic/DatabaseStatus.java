package dev.novadev.novabank.bukkit.diagnostic;

import dev.novadev.novabank.core.text.ConsoleText;
import java.util.Objects;
import java.util.Optional;

/**
 * Resultat d'une requete de verification sur la base.
 *
 * @param schemaVersion version de schema relue, vide en cas d'echec
 * @param error         cause de l'echec, vide en cas de succes
 * @param latencyMillis duree de la requete
 */
public record DatabaseStatus(Optional<Integer> schemaVersion, Optional<String> error, long latencyMillis) {

    /**
     * @throws IllegalArgumentException si ni version ni erreur, ou les deux
     */
    public DatabaseStatus {
        Objects.requireNonNull(schemaVersion, "schemaVersion");
        Objects.requireNonNull(error, "error");
        if (schemaVersion.isPresent() == error.isPresent()) {
            throw new IllegalArgumentException(ConsoleText.tr("Version ou erreur, exactement une des deux"));
        }
    }

    /**
     * @param schemaVersion version relue
     * @param latencyMillis duree
     * @return statut d'une base accessible
     */
    public static DatabaseStatus reachable(int schemaVersion, long latencyMillis) {
        return new DatabaseStatus(Optional.of(schemaVersion), Optional.empty(), latencyMillis);
    }

    /**
     * @param error         cause
     * @param latencyMillis duree
     * @return statut d'une base inaccessible
     */
    public static DatabaseStatus unreachable(String error, long latencyMillis) {
        return new DatabaseStatus(Optional.empty(), Optional.of(error), latencyMillis);
    }
}
