package dev.novadev.novabank.platform.folia;

import dev.novadev.novabank.bukkit.scheduler.IoExecutor;
import dev.novadev.novabank.bukkit.scheduler.PluginScheduler;
import dev.novadev.novabank.bukkit.scheduler.ShutdownTasks;
import dev.novadev.novabank.core.application.ScheduledTask;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Planificateur Folia : il n'y a pas de thread principal.
 *
 * <ul>
 *   <li>contexte joueur : planificateur de l'entite du joueur ({@code Player#getScheduler()}),
 *       donc thread de la region qui possede le joueur, ou qu'il se deplace ; si le joueur
 *       est parti (absent, ou entite retiree avant l'execution), la branche "joueur parti"
 *       est executee a la place, sans toucher au joueur : par le rappel "retired" de Folia
 *       (thread qui retire l'entite) ou par la region globale ;</li>
 *   <li>contexte global : planificateur de la region globale ;</li>
 *   <li>entrees/sorties : {@link IoExecutor} du plugin et non l'AsyncScheduler de Folia (pool
 *       partage par tous les plugins, sans borne) : file bornee, threads nommes, fermeture
 *       ordonnee avant le stockage, identiques sous Bukkit ;</li>
 *   <li>taches periodiques : minuteur de la region globale qui confie chaque execution a
 *       l'executeur IO (meme cadence en ticks que sous Bukkit).</li>
 * </ul>
 *
 * <p>Folia refuse un delai nul : un delai de 0 tick est porte a 1 tick (comme
 * {@code GlobalRegionScheduler#run}). Le planificateur Bukkit n'est jamais utilise (Folia leve
 * {@link UnsupportedOperationException}).
 *
 * <p>Arret : Folia arrete les planificateurs de toutes les regions AVANT de desactiver les
 * plugins, et ignore sans le signaler toute tache d'un plugin desactive (ni execution, ni
 * rappel "retired"). Les taches deja confiees a Folia sont donc reprises par la pompe
 * d'arret de {@link PluginScheduler}.
 */
public final class FoliaTaskScheduler extends PluginScheduler {

    private final Server server;
    private final GlobalRegionScheduler global;

    /**
     * @param plugin plugin proprietaire des taches
     * @param server serveur Folia
     * @param io     executeur des entrees/sorties
     * @param logger journal
     */
    public FoliaTaskScheduler(Plugin plugin, Server server, IoExecutor io, Logger logger) {
        super(plugin, io, logger);
        this.server = Objects.requireNonNull(server, "server");
        this.global = Objects.requireNonNull(server.getGlobalRegionScheduler(), "globalRegionScheduler");
    }

    /**
     * Sous Folia, un joueur n'est manipulable que depuis le thread de sa region (ou le
     * thread d'arret du serveur, une fois toutes les regions arretees).
     */
    @Override
    public boolean canAccessPlayer(UUID playerId) {
        Player player = server.getPlayer(playerId);
        return player != null && server.isOwnedByCurrentRegion(player);
    }

    @Override
    protected void scheduleGlobal(Runnable task) {
        global.execute(plugin(), task);
    }

    @Override
    protected void schedulePlayer(UUID playerId, ShutdownTasks.Tracked task, Runnable ifOffline) {
        Player player = server.getPlayer(playerId);
        if (player != null
                && player.getScheduler().run(plugin(), scheduled -> task.run(), () -> task.runInstead(ifOffline)) != null) {
            return;
        }
        // Joueur absent, ou entite deja retiree (Folia renvoie null sans appeler le rappel).
        global.execute(plugin(), () -> task.runInstead(ifOffline));
    }

    @Override
    protected ScheduledTask scheduleLaterGlobal(Runnable task, long ticks) {
        return new FoliaScheduledTask(global.runDelayed(plugin(), scheduled -> task.run(), Math.max(1L, ticks)));
    }

    @Override
    protected ScheduledTask scheduleTimer(Runnable trigger, long delayTicks, long periodTicks) {
        return new FoliaScheduledTask(global.runAtFixedRate(plugin(), scheduled -> trigger.run(),
                Math.max(1L, delayTicks), periodTicks));
    }

    private record FoliaScheduledTask(io.papermc.paper.threadedregions.scheduler.ScheduledTask task)
            implements ScheduledTask {

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
