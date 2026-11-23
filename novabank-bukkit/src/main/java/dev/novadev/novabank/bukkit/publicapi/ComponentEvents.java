package dev.novadev.novabank.bukkit.publicapi;

import dev.novadev.novabank.api.event.ComponentStateEvent;
import org.bukkit.plugin.PluginManager;

/**
 * Publication des {@link ComponentStateEvent}. Un ecouteur defaillant ne doit jamais empecher le demarrage.
 */
public final class ComponentEvents {

    private ComponentEvents() {
    }

    /**
     * @param manager   gestionnaire de plugins du serveur
     * @param kind      famille du composant
     * @param name      nom du composant
     * @param available {@code true} s'il est utilisable
     * @param detail    precision lisible, sans secret
     */
    public static void fire(PluginManager manager, ComponentStateEvent.Kind kind, String name, boolean available,
                            String detail) {
        try {
            manager.callEvent(new ComponentStateEvent(kind, name, available, detail));
        } catch (RuntimeException failure) {
            // Evenement d'information : ignore.
        }
    }
}
