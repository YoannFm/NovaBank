package dev.novadev.novabank.bukkit.scheduler;

import dev.novadev.novabank.core.text.ConsoleText;
import java.time.Duration;
import java.util.Objects;

/**
 * Conversion des durees en ticks serveur (20 par seconde).
 */
public final class Ticks {

    /** Duree d'un tick. */
    public static final Duration TICK = Duration.ofMillis(50);

    private Ticks() {
    }

    /**
     * Arrondi au tick superieur : une tache n'est jamais executee avant le delai demande.
     *
     * @param duration duree positive ou nulle
     * @return nombre de ticks
     * @throws IllegalArgumentException si la duree est negative
     */
    public static long ceil(Duration duration) {
        Objects.requireNonNull(duration, "duration");
        if (duration.isNegative()) {
            throw new IllegalArgumentException(ConsoleText.tr("Duree negative : {0}", duration));
        }
        long millis = duration.toMillis();
        long tickMillis = TICK.toMillis();
        return (millis + tickMillis - 1) / tickMillis;
    }

    /**
     * Periode en ticks, au moins un tick.
     *
     * @param period periode strictement positive
     * @return nombre de ticks, 1 minimum
     * @throws IllegalArgumentException si la periode est nulle ou negative
     */
    public static long period(Duration period) {
        Objects.requireNonNull(period, "period");
        if (period.isZero() || period.isNegative()) {
            throw new IllegalArgumentException(ConsoleText.tr("Periode non positive : {0}", period));
        }
        return Math.max(1L, ceil(period));
    }
}
