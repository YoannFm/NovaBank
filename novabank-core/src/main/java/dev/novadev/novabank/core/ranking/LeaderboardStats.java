package dev.novadev.novabank.core.ranking;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Etat des classements, pour le diagnostic.
 *
 * @param entries         nombre de banques classees par type
 * @param lastRebuild     fin du dernier recalcul complet reussi
 * @param lastRebuildTook duree de ce recalcul
 * @param lastError       derniere erreur de recalcul (sans secret), vide si le dernier a reussi
 * @param refreshes       banques recalculees individuellement (changements locaux et distants)
 */
public record LeaderboardStats(Map<String, Integer> entries, Optional<Instant> lastRebuild,
                               Optional<Duration> lastRebuildTook, Optional<String> lastError, long refreshes) {

    /**
     * Copie defensive des tables.
     */
    public LeaderboardStats {
        entries = Map.copyOf(entries);
    }
}
