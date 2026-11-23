package dev.novadev.novabank.core.sync;

import dev.novadev.novabank.core.text.ConsoleText;
import java.time.Duration;
import java.util.Objects;

/**
 * Reglages temporels de la reconciliation.
 *
 * @param pollInterval periode de lecture du flux de changements, strictement positive
 * @param overlap      recouvrement des lectures successives, strictement positif : absorbe l'ecart
 *                     entre les horloges des serveurs et la visibilite tardive d'une transaction
 * @param pageSize     nombre maximal de banques lues a la fois, strictement positif
 */
public record SyncTiming(Duration pollInterval, Duration overlap, int pageSize) {

    /** Periode par defaut. */
    public static final Duration DEFAULT_POLL_INTERVAL = Duration.ofSeconds(5);
    /** Recouvrement par defaut. */
    public static final Duration DEFAULT_OVERLAP = Duration.ofSeconds(30);
    /** Taille de page par defaut. */
    public static final int DEFAULT_PAGE_SIZE = 1000;

    /**
     * @throws NullPointerException     si une duree est nulle
     * @throws IllegalArgumentException si une valeur n'est pas strictement positive
     */
    public SyncTiming {
        Objects.requireNonNull(pollInterval, "pollInterval");
        Objects.requireNonNull(overlap, "overlap");
        if (pollInterval.isZero() || pollInterval.isNegative() || overlap.isZero() || overlap.isNegative()
                || pageSize <= 0) {
            throw new IllegalArgumentException(ConsoleText.tr("Reglages de synchronisation invalides : {0}, {1}, {2}",
                    pollInterval, overlap, pageSize));
        }
    }

    /**
     * @return les reglages par defaut
     */
    public static SyncTiming defaults() {
        return new SyncTiming(DEFAULT_POLL_INTERVAL, DEFAULT_OVERLAP, DEFAULT_PAGE_SIZE);
    }
}
