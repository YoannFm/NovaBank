package dev.novadev.novabank.core.sync;

import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.core.application.ScheduledTask;
import dev.novadev.novabank.core.application.TaskScheduler;
import dev.novadev.novabank.core.text.ConsoleText;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Combine le bus rapide et la lecture du stockage pour signaler au {@link RemoteChangeListener}
 * chaque changement fait par un autre serveur, au moins une fois et sans doublon pour une meme
 * version.
 *
 * <ul>
 *   <li>Apres chaque ecriture locale, {@link #localChange} diffuse la nouvelle version sur le bus
 *       (meilleur effort : un bus coupe n'empeche jamais l'operation).</li>
 *   <li>{@link #poll} relit le stockage depuis le dernier changement vu moins un recouvrement ;
 *       il rattrape les messages perdus, les serveurs tombes avant de notifier et les coupures
 *       du bus.</li>
 *   <li>Une version deja connue (vue par le bus, par la lecture ou ecrite ici) n'est pas
 *       redistribuee ; les ecritures de ce serveur ne lui sont jamais signalees.</li>
 * </ul>
 *
 * <p>Sure entre threads.
 */
public final class SyncCoordinator implements AutoCloseable, LocalChangeSink {

    /** Les entrees plus vieilles que ce multiple du recouvrement, avant le curseur, sont oubliees. */
    private static final int RETENTION_FACTOR = 10;

    private record Seen(long version, Instant at) {
    }

    private final String serverId;
    private final BankChangeFeed feed;
    private final SyncBus bus;
    private final RemoteChangeListener listener;
    private final Clock clock;
    private final SyncTiming timing;
    private final Logger logger;
    private final Map<BankId, Seen> known = new ConcurrentHashMap<>();
    private final AtomicReference<Instant> cursor;
    private final AtomicReference<Instant> lastPoll = new AtomicReference<>();
    private final AtomicReference<String> lastPollError = new AtomicReference<>();
    private final AtomicLong published = new AtomicLong();
    private final AtomicLong publishRefused = new AtomicLong();
    private final AtomicLong fromBus = new AtomicLong();
    private final AtomicLong fromStorage = new AtomicLong();
    private volatile ScheduledTask pollTask;
    private volatile Consumer<String> controlHandler;

    /**
     * @param serverId identifiant de ce serveur dans le reseau, non vide
     * @param feed     flux de changements du stockage partage
     * @param bus      bus de notification, ou {@code null} pour ne s'appuyer que sur la lecture
     * @param listener receveur des changements distants
     * @param clock    horloge
     * @param timing   reglages de reconciliation
     * @param logger   journal du plugin
     */
    public SyncCoordinator(String serverId, BankChangeFeed feed, SyncBus bus, RemoteChangeListener listener,
                           Clock clock, SyncTiming timing, Logger logger) {
        if (Objects.requireNonNull(serverId, "serverId").isBlank()) {
            throw new IllegalArgumentException(ConsoleText.tr("Identifiant de serveur vide"));
        }
        this.serverId = serverId;
        this.feed = Objects.requireNonNull(feed, "feed");
        this.bus = bus;
        this.listener = Objects.requireNonNull(listener, "listener");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.timing = Objects.requireNonNull(timing, "timing");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.cursor = new AtomicReference<>(clock.instant());
    }

    /**
     * Branche le bus et lance la reconciliation periodique. Le curseur part de maintenant :
     * l'historique anterieur au demarrage n'interesse aucun cache (ils sont vides).
     *
     * @param scheduler planificateur dont l'executeur asynchrone lit le stockage
     */
    public void start(TaskScheduler scheduler) {
        if (bus != null) {
            bus.subscribe(this::onBusEvent);
            bus.subscribeControl((origin, command) -> {
                Consumer<String> handler = controlHandler;
                if (handler != null && !origin.equals(serverId)) {
                    try {
                        handler.accept(command);
                    } catch (RuntimeException failure) {
                        logger.log(Level.WARNING,
                                ConsoleText.tr("Synchronisation : commande de controle '{0}' en echec",
                                        command),
                                failure);
                    }
                }
            });
        }
        pollTask = scheduler.runRepeatingAsync(this::poll, timing.pollInterval(), timing.pollInterval());
    }

    /**
     * @param handler receveur des commandes de controle envoyees par les AUTRES serveurs (jamais par celui-ci),
     *                appele depuis un thread du bus : doit reporter son travail sur le planificateur
     */
    public void onControl(Consumer<String> handler) {
        this.controlHandler = Objects.requireNonNull(handler, "handler");
    }

    /**
     * Demande aux autres serveurs d'executer une commande de controle (par exemple {@code reload}).
     *
     * @param command commande
     * @return {@code true} si le bus l'a acceptee ; sinon les autres serveurs ne sont pas prevenus
     */
    public boolean broadcastControl(String command) {
        return bus != null && bus.publishControl(serverId, command);
    }

    /**
     * Diffuse un changement qui vient d'etre enregistre par ce serveur.
     *
     * @param change changement local
     */
    @Override
    public void localChange(BankChange change) {
        known.merge(change.bankId(), new Seen(change.version(), change.updatedAt()),
                (old, fresh) -> fresh.version() > old.version() ? fresh : old);
        if (bus == null) {
            return;
        }
        if (bus.publish(new SyncEvent(serverId, change))) {
            published.incrementAndGet();
        } else {
            publishRefused.incrementAndGet();
        }
    }

    @Override
    public void noteAnnounceFailure(RuntimeException failure) {
        publishRefused.incrementAndGet();
        logger.log(Level.WARNING, ConsoleText.tr("Synchronisation : notification locale en echec"), failure);
    }

    /**
     * Lit le flux de changements du stockage et signale les nouveautes. Appele par la tache
     * periodique, ou directement par les tests. Une panne du stockage est consignee et la
     * lecture reprise a la periode suivante, depuis le meme curseur.
     */
    public void poll() {
        try {
            Instant newCursor = cursor.get();
            Instant since = newCursor.minus(timing.overlap());
            List<BankChange> page;
            do {
                page = feed.changesSince(since, timing.pageSize());
                for (BankChange change : page) {
                    if (accept(change)) {
                        fromStorage.incrementAndGet();
                    }
                    if (change.updatedAt().isAfter(newCursor)) {
                        newCursor = change.updatedAt();
                    }
                }
                since = page.isEmpty() ? since : page.get(page.size() - 1).updatedAt();
                // Page pleine d'une seule date : on avance d'une milliseconde pour ne pas boucler.
                if (page.size() == timing.pageSize() && page.get(0).updatedAt().equals(since)) {
                    since = since.plusMillis(1);
                }
            } while (page.size() == timing.pageSize());
            cursor.set(newCursor);
            lastPoll.set(clock.instant());
            lastPollError.set(null);
            prune(newCursor);
        } catch (RuntimeException failure) {
            lastPollError.set(failure.getClass().getSimpleName() + " : " + failure.getMessage());
            logger.log(Level.WARNING,
                    ConsoleText.tr("Synchronisation : lecture des changements en echec, nouvel essai a la prochaine periode"),
                            failure);
        }
    }

    /**
     * @return les compteurs courants
     */
    public SyncStats stats() {
        return new SyncStats(serverId, published.get(), publishRefused.get(), fromBus.get(), fromStorage.get(),
                Optional.ofNullable(lastPoll.get()), Optional.ofNullable(lastPollError.get()));
    }

    /**
     * @return l'etat du bus, vide si la synchronisation n'utilise que la lecture du stockage
     */
    public Optional<SyncBusStatus> busStatus() {
        return Optional.ofNullable(bus).map(SyncBus::status);
    }

    @Override
    public void close() {
        ScheduledTask task = pollTask;
        if (task != null) {
            task.cancel();
        }
    }

    private void onBusEvent(SyncEvent event) {
        if (event.originServer().equals(serverId)) {
            return;
        }
        if (accept(event.change())) {
            fromBus.incrementAndGet();
        }
    }

    /** @return {@code true} si la version etait nouvelle et a ete signalee au receveur */
    private boolean accept(BankChange change) {
        boolean[] fresh = {false};
        known.compute(change.bankId(), (id, old) -> {
            if (old != null && old.version() >= change.version()) {
                return old;
            }
            fresh[0] = true;
            return new Seen(change.version(), change.updatedAt());
        });
        if (fresh[0]) {
            try {
                listener.onRemoteChange(change);
            } catch (RuntimeException failure) {
                logger.log(Level.WARNING, ConsoleText.tr("Synchronisation : le receveur a echoue pour la banque {0}",
                        change.bankId()),
                        failure);
            }
        }
        return fresh[0];
    }

    private void prune(Instant newCursor) {
        Instant limit = newCursor.minus(timing.overlap().multipliedBy(RETENTION_FACTOR));
        known.values().removeIf(seen -> seen.at().isBefore(limit));
    }
}
