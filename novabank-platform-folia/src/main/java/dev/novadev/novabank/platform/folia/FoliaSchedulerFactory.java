package dev.novadev.novabank.platform.folia;

import dev.novadev.novabank.bukkit.scheduler.IoExecutor;
import dev.novadev.novabank.bukkit.scheduler.PluginScheduler;
import dev.novadev.novabank.bukkit.scheduler.SchedulerFactory;
import java.util.logging.Logger;
import org.bukkit.plugin.Plugin;

/**
 * Point d'entree du module Folia, instancie par son nom
 * ({@code PlatformSchedulers#FOLIA_FACTORY}) uniquement quand Folia est detecte.
 */
public final class FoliaSchedulerFactory implements SchedulerFactory {

    /**
     * Constructeur public sans argument, requis par le chargement par nom.
     */
    public FoliaSchedulerFactory() {
        // Sans etat.
    }

    @Override
    public PluginScheduler create(Plugin plugin, IoExecutor io, Logger logger) {
        return new FoliaTaskScheduler(plugin, plugin.getServer(), io, logger);
    }
}
