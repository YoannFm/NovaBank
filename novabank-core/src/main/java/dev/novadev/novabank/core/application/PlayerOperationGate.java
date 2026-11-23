package dev.novadev.novabank.core.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Verrou logique par joueur et delai minimal entre deux operations.
 *
 * <p>Un seul flux (depot, retrait, remise de livraisons) en cours par joueur sur ce
 * serveur : un second est refuse ("operation deja en cours"), jamais mis en file. Le delai
 * se compte entre les debuts de deux operations acceptees. Sur pour plusieurs threads.
 */
public final class PlayerOperationGate {

    private static final int PRUNE_THRESHOLD = 1_000;

    private final Clock clock;
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Instant> lastStart = new ConcurrentHashMap<>();

    /**
     * @param clock horloge du delai
     */
    public PlayerOperationGate(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Tente de commencer une operation soumise au delai.
     *
     * @param playerId joueur
     * @param cooldown delai minimal depuis le debut de sa precedente operation
     * @return l'issue ; si elle est acceptee, l'appelant doit appeler {@link #leave(UUID)}
     */
    public Entry tryEnter(UUID playerId, Duration cooldown) {
        Objects.requireNonNull(cooldown, "cooldown");
        if (!inFlight.add(Objects.requireNonNull(playerId, "playerId"))) {
            return Entry.BUSY;
        }
        Instant now = clock.instant();
        Instant previous = lastStart.get(playerId);
        if (previous != null) {
            Duration remaining = Duration.between(now, previous.plus(cooldown));
            if (remaining.compareTo(Duration.ZERO) > 0) {
                inFlight.remove(playerId);
                return new Entry(Status.COOLDOWN, remaining);
            }
        }
        lastStart.put(playerId, now);
        if (lastStart.size() > PRUNE_THRESHOLD) {
            lastStart.values().removeIf(start -> start.plus(cooldown).isBefore(now));
        }
        return Entry.ACCEPTED;
    }

    /**
     * Tente de prendre le verrou du joueur sans delai (remise des livraisons en attente).
     *
     * @param playerId joueur
     * @return {@code true} si le verrou est pris ; l'appelant doit alors appeler {@link #leave(UUID)}
     */
    public boolean tryLock(UUID playerId) {
        return inFlight.add(Objects.requireNonNull(playerId, "playerId"));
    }

    /**
     * Libere le verrou du joueur.
     *
     * @param playerId joueur
     */
    public void leave(UUID playerId) {
        inFlight.remove(playerId);
    }

    /** Issue d'une tentative. */
    public enum Status {
        /** Operation acceptee, verrou pris. */
        ACCEPTED,
        /** Une operation est deja en cours. */
        BUSY,
        /** Delai non ecoule. */
        COOLDOWN
    }

    /**
     * @param status    issue
     * @param remaining attente restante pour {@link Status#COOLDOWN}, sinon zero
     */
    public record Entry(Status status, Duration remaining) {

        static final Entry ACCEPTED = new Entry(Status.ACCEPTED, Duration.ZERO);
        static final Entry BUSY = new Entry(Status.BUSY, Duration.ZERO);

        /**
         * @throws NullPointerException si un champ est nul
         */
        public Entry {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(remaining, "remaining");
        }
    }
}
