package dev.novadev.novabank.bukkit.scheduler;

import dev.novadev.novabank.core.application.ScheduledTask;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

/**
 * Planificateur Bukkit/Paper (hors Folia) : contextes global et joueur sur le thread
 * principal, entrees/sorties sur l'{@link IoExecutor} du plugin (voir {@link PluginScheduler}).
 * Les taches periodiques asynchrones sont cadencees par un minuteur du serveur (leger, thread
 * principal) qui confie chaque execution a l'executeur IO.
 */
public final class BukkitTaskScheduler extends PluginScheduler {

    private final BukkitScheduler scheduler;
    private final PlayerPresence players;

    /**
     * @param plugin    plugin proprietaire des taches
     * @param scheduler planificateur du serveur
     * @param io        executeur des entrees/sorties
     * @param players   presence des joueurs
     * @param logger    journal
     */
    public BukkitTaskScheduler(Plugin plugin, BukkitScheduler scheduler, IoExecutor io, PlayerPresence players,
                               Logger logger) {
        super(plugin, io, logger);
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.players = Objects.requireNonNull(players, "players");
    }

    /**
     * Sous Bukkit, tout joueur connecte est manipulable depuis le thread principal, seul
     * thread qui execute les taches des contextes global et joueur (y compris la pompe
     * d'arret, lancee par le thread qui desactive le plugin).
     */
    @Override
    public boolean canAccessPlayer(UUID playerId) {
        return players.isOnline(playerId);
    }

    @Override
    protected void scheduleGlobal(Runnable task) {
        scheduler.runTask(plugin(), task);
    }

    @Override
    protected void schedulePlayer(UUID playerId, ShutdownTasks.Tracked task, Runnable ifOffline) {
        scheduler.runTask(plugin(), () -> {
            if (players.isOnline(playerId)) {
                task.run();
            } else {
                task.runInstead(ifOffline);
            }
        });
    }

    @Override
    protected ScheduledTask scheduleLaterGlobal(Runnable task, long ticks) {
        return new BukkitScheduledTask(scheduler.runTaskLater(plugin(), task, ticks));
    }

    @Override
    protected ScheduledTask scheduleTimer(Runnable trigger, long delayTicks, long periodTicks) {
        return new BukkitScheduledTask(scheduler.runTaskTimer(plugin(), trigger, delayTicks, periodTicks));
    }

    private record BukkitScheduledTask(BukkitTask task) implements ScheduledTask {

        @Override
        public void cancel() {
            task.cancel();
        }

        @Override
        public boolean isCancelled() {
            return task.isCancelled();
        }
    }
}
