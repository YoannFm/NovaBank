package dev.novadev.novabank.bukkit.scheduler;

import dev.novadev.novabank.core.application.FlowDrain;
import dev.novadev.novabank.core.application.ScheduledTask;
import dev.novadev.novabank.core.application.TaskScheduler;
import dev.novadev.novabank.core.text.ConsoleText;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;

/**
 * Base commune des planificateurs de plateforme (Bukkit/Paper et Folia) : entrees/sorties
 * sur l'{@link IoExecutor} du plugin, taches periodiques sans empilement, et phase d'arret.
 * Chaque plateforme ne fournit que la planification sur ses propres threads.
 *
 * <p>Arret : une fois le plugin desactive, le serveur n'execute plus ses taches. Les taches
 * des contextes global et joueur deja confiees au serveur mais non executees, et celles
 * soumises ensuite, sont alors executees par la pompe de {@link #startDraining()} sur le
 * thread qui desactive le plugin (phase d'arret, {@link FlowDrain}) ; une tache "joueur" n'y
 * est executee que si le joueur est connecte et manipulable depuis ce thread
 * ({@link #canAccessPlayer}), sinon sa branche "joueur parti" l'est a la place. Apres
 * {@link #stopDraining()}, les taches sont ignorees et signalees. Les taches differees et
 * periodiques soumises plugin desactive sont ignorees et signalees.
 */
public abstract class PluginScheduler implements TaskScheduler {

    private final Plugin plugin;
    private final IoExecutor io;
    private final Logger logger;
    private final ShutdownTasks shutdown;

    /**
     * @param plugin plugin proprietaire des taches
     * @param io     executeur des entrees/sorties
     * @param logger journal
     */
    protected PluginScheduler(Plugin plugin, IoExecutor io, Logger logger) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.io = Objects.requireNonNull(io, "io");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.shutdown = new ShutdownTasks(logger);
    }

    /**
     * @return plugin proprietaire des taches
     */
    protected final Plugin plugin() {
        return plugin;
    }

    /**
     * Indique si un joueur est connecte et peut etre manipule (inventaire, entite) depuis le
     * thread appelant : thread principal sous Bukkit, region du joueur sous Folia.
     *
     * @param playerId joueur
     * @return {@code true} si le joueur est connecte et manipulable ici
     */
    public abstract boolean canAccessPlayer(UUID playerId);

    /**
     * Confie au serveur une tache du contexte global (au prochain tick).
     *
     * @param task tache suivie
     * @throws IllegalPluginAccessException si le plugin vient d'etre desactive
     */
    protected abstract void scheduleGlobal(Runnable task);

    /**
     * Confie au serveur une tache a executer sur le thread qui possede le joueur. Si le
     * joueur n'est pas (ou plus) connecte, la plateforme appelle
     * {@code task.runInstead(ifOffline)} sans toucher au joueur.
     *
     * @param playerId  joueur
     * @param task      tache suivie
     * @param ifOffline branche "joueur parti"
     * @throws IllegalPluginAccessException si le plugin vient d'etre desactive
     */
    protected abstract void schedulePlayer(UUID playerId, ShutdownTasks.Tracked task, Runnable ifOffline);

    /**
     * @param task  tache
     * @param ticks delai en ticks, positif ou nul
     * @return la tache planifiee
     */
    protected abstract ScheduledTask scheduleLaterGlobal(Runnable task, long ticks);

    /**
     * Minuteur leger (contexte global) qui declenche une tache periodique.
     *
     * @param trigger      declencheur, rapide
     * @param delayTicks   delai initial en ticks, positif ou nul
     * @param periodTicks  periode en ticks, 1 minimum
     * @return la tache planifiee
     */
    protected abstract ScheduledTask scheduleTimer(Runnable trigger, long delayTicks, long periodTicks);

    @Override
    public final void runGlobal(Runnable task) {
        Objects.requireNonNull(task, "task");
        submit(task, task, this::scheduleGlobal);
    }

    @Override
    public final void runForPlayer(UUID playerId, Runnable task, Runnable ifOffline) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(ifOffline, "ifOffline");
        Runnable whenDrained = () -> {
            if (canAccessPlayer(playerId)) {
                task.run();
            } else {
                ifOffline.run();
            }
        };
        submit(task, whenDrained, tracked -> schedulePlayer(playerId, tracked, ifOffline));
    }

    @Override
    public final void runAsync(Runnable task) {
        io.execute(task);
    }

    @Override
    public final ScheduledTask runLaterGlobal(Runnable task, Duration delay) {
        Objects.requireNonNull(task, "task");
        long ticks = Ticks.ceil(delay);
        if (!acceptsTasks()) {
            return CancelledTask.INSTANCE;
        }
        return scheduleLaterGlobal(task, ticks);
    }

    @Override
    public final ScheduledTask runRepeatingAsync(Runnable task, Duration initialDelay, Duration period) {
        Objects.requireNonNull(task, "task");
        long delayTicks = Ticks.ceil(initialDelay);
        long periodTicks = Ticks.period(period);
        if (!acceptsTasks()) {
            return CancelledTask.INSTANCE;
        }
        AtomicBoolean running = new AtomicBoolean();
        Runnable trigger = () -> {
            if (!running.compareAndSet(false, true)) {
                return;
            }
            try {
                io.execute(() -> {
                    try {
                        task.run();
                    } finally {
                        running.set(false);
                    }
                });
            } catch (RejectedExecutionException rejected) {
                running.set(false);
                logger.warning(ConsoleText.tr("Tache periodique NovaBank non lancee (executeur IO sature ou arrete)"));
            }
        };
        return scheduleTimer(trigger, delayTicks, periodTicks);
    }

    /**
     * Debut de l'arret : les taches des contextes global et joueur ne passent plus par le
     * serveur (qui ne les executerait plus) mais par une file executee par la pompe rendue,
     * a appeler depuis le thread qui desactive le plugin.
     *
     * @return la pompe des taches en attente
     */
    public final FlowDrain.Pump startDraining() {
        shutdown.startDraining();
        return shutdown::runPending;
    }

    /**
     * Fin de l'arret : les taches soumises ensuite sont ignorees.
     *
     * @return nombre de taches restees en attente, jamais executees
     */
    public final int stopDraining() {
        return shutdown.close();
    }

    private void submit(Runnable task, Runnable whenDrained, Consumer<ShutdownTasks.Tracked> platform) {
        Runnable queued = whenDrained;
        if (plugin.isEnabled() && !shutdown.isDraining()) {
            ShutdownTasks.Tracked tracked = shutdown.track(task, whenDrained);
            try {
                platform.accept(tracked);
                return;
            } catch (IllegalPluginAccessException disabledMeanwhile) {
                // Desactive entre la verification et la planification : file d'arret (la
                // tache suivie n'est executee qu'une fois).
                queued = () -> tracked.runInstead(whenDrained);
            }
        }
        if (!shutdown.offer(queued)) {
            logger.warning(ConsoleText.tr("Tache ignoree : NovaBank est arrete"));
        }
    }

    private boolean acceptsTasks() {
        if (plugin.isEnabled()) {
            return true;
        }
        logger.warning(ConsoleText.tr("Tache ignoree : NovaBank est desactive (arret en cours)"));
        return false;
    }

    private enum CancelledTask implements ScheduledTask {
        INSTANCE;

        @Override
        public void cancel() {
            // Jamais planifiee.
        }

        @Override
        public boolean isCancelled() {
            return true;
        }
    }
}
