package dev.novadev.novabank.bukkit.lifecycle;

import dev.novadev.novabank.bukkit.message.LogMessages;
import dev.novadev.novabank.bukkit.scheduler.PluginScheduler;
import dev.novadev.novabank.core.application.FlowDrain;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Phase d'arret ("drain") executee a la desactivation, AVANT la fermeture de l'executeur
 * IO et du stockage : nouveaux depots/retraits refuses, operations en cours terminees
 * (dans la limite du delai configure) en executant sur le thread qui desactive le plugin
 * les taches que le serveur n'execute plus. Voir {@link FlowDrain} et {@link PluginScheduler}.
 *
 * <p>Bukkit/Paper : le thread qui desactive le plugin est le thread principal. Folia : les
 * planificateurs de toutes les regions (et le global) sont arretes AVANT la desactivation
 * des plugins, qui a lieu sur le thread d'arret du serveur ; aucune tache du plugin ne
 * sera plus executee par Folia, la pompe les execute toutes. Dans les deux cas, pendant
 * l'arret, les etapes "inventaire" des flux prennent la branche "joueur parti" (livraison
 * en attente ou solde D13), voir {@link FlowDrain}.
 */
final class ShutdownDrain implements AutoCloseable {

    private final FlowDrain drain;
    private final PluginScheduler scheduler;
    private final Supplier<Duration> timeout;
    private final Logger logger;

    /**
     * @param drain     suivi des operations en cours
     * @param scheduler planificateur (file des taches a terminer pendant l'arret)
     * @param timeout   attente maximale, lue a l'arret (configuration en vigueur)
     * @param logger    journal
     */
    ShutdownDrain(FlowDrain drain, PluginScheduler scheduler, Supplier<Duration> timeout, Logger logger) {
        this.drain = Objects.requireNonNull(drain, "drain");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public void close() {
        int active = drain.activeFlows();
        Duration limit = timeout.get();
        if (active > 0) {
            logger.info(LogMessages.drainWaiting(active, limit.toMillis()));
        }
        long start = System.nanoTime();
        boolean done = drain.drain(limit, scheduler.startDraining());
        int abandoned = scheduler.stopDraining();
        long elapsed = (System.nanoTime() - start) / 1_000_000L;
        if (!done) {
            logger.severe(LogMessages.drainIncomplete(drain.activeFlows(), elapsed, abandoned));
        } else if (active > 0) {
            logger.info(LogMessages.drainDone(elapsed));
        }
    }
}
