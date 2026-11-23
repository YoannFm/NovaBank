package dev.novadev.novabank.bukkit.command;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Confirmation obligatoire d'une action sensible (reset, migration) : une demande par
 * administrateur, valable pendant une duree limitee, consommee par la confirmation. Sans
 * dependance serveur.
 *
 * @param <T> cible de l'action (joueur, type de stockage)
 */
public final class PendingConfirmations<T> {

    /** Delai de confirmation. */
    public static final Duration WINDOW = Duration.ofSeconds(30);

    private final Clock clock;
    private final Map<String, Request<T>> requests = new ConcurrentHashMap<>();

    /**
     * @param clock horloge
     */
    public PendingConfirmations(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Enregistre (ou remplace) la demande d'un administrateur.
     *
     * @param actor  administrateur
     * @param target cible de l'action
     */
    public void request(String actor, T target) {
        requests.put(actor, new Request<>(target, clock.instant().plus(WINDOW)));
    }

    /**
     * Consomme la demande si elle vise cette cible et n'a pas expire.
     *
     * @param actor  administrateur
     * @param target cible visee par la confirmation
     * @return {@code true} si l'action est confirmee
     */
    public boolean confirm(String actor, T target) {
        Request<T> request = requests.get(actor);
        if (request == null || !request.target().equals(target)) {
            return false;
        }
        requests.remove(actor);
        return !clock.instant().isAfter(request.expires());
    }

    private record Request<T>(T target, Instant expires) {
    }
}
