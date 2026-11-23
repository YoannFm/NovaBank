package dev.novadev.novabank.bukkit.config;

/**
 * Indique si un plugin est installe et active.
 */
@FunctionalInterface
public interface PluginPresence {

    /**
     * @param pluginName nom declare dans le plugin.yml du plugin
     * @return {@code true} s'il est active
     */
    boolean isEnabled(String pluginName);
}
