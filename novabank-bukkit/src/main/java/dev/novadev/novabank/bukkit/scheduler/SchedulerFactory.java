package dev.novadev.novabank.bukkit.scheduler;

import java.util.logging.Logger;
import org.bukkit.plugin.Plugin;

/**
 * Creation du planificateur propre a une plateforme. L'implementation Folia vit dans le
 * module {@code novabank-platform-folia} et n'est chargee, par son nom, que sous Folia
 * ({@link PlatformSchedulers}) : elle doit avoir un constructeur public sans argument.
 */
@FunctionalInterface
public interface SchedulerFactory {

    /**
     * @param plugin plugin proprietaire des taches
     * @param io     executeur des entrees/sorties
     * @param logger journal
     * @return le planificateur
     */
    PluginScheduler create(Plugin plugin, IoExecutor io, Logger logger);
}
