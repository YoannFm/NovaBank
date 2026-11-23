package dev.novadev.novabank.bukkit.item;

import dev.novadev.novabank.api.event.ComponentStateEvent;
import dev.novadev.novabank.bukkit.publicapi.ComponentEvents;
import dev.novadev.novabank.core.text.ConsoleText;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

/**
 * Enregistre les fournisseurs des plugins d'items custom installes et ecoute leur evenement de fin
 * de chargement : leurs items n'existent pas toujours au demarrage de NovaBank (ItemsAdder charge de
 * facon asynchrone), le catalogue est alors reevalue par l'appelant ({@code onItemsLoaded}).
 */
public final class CustomItemRegistration {

    private record Candidate(CustomItemPlugins.Spec spec, Function<ClassLoader, Optional<ItemProvider>> factory) {
    }

    private static final List<Candidate> CANDIDATES = List.of(
            new Candidate(CustomItemPlugins.ITEMSADDER, CustomItemPlugins::itemsAdder),
            new Candidate(CustomItemPlugins.NEXO, CustomItemPlugins::nexo),
            new Candidate(CustomItemPlugins.ORAXEN, CustomItemPlugins::oraxen),
            new Candidate(CustomItemPlugins.CRAFTENGINE, CustomItemPlugins::craftEngine),
            new Candidate(CustomItemPlugins.MMOITEMS, CustomItemPlugins::mmoItems));

    private CustomItemRegistration() {
    }

    /**
     * @param owner         plugin NovaBank (proprietaire des ecouteurs)
     * @param manager       gestionnaire de plugins du serveur
     * @param items         registre des fournisseurs
     * @param logger        journal du plugin
     * @param loaded        recoit une description de chaque fournisseur charge (nom, version, namespace)
     * @param onItemsLoaded appele (thread du serveur) quand un plugin signale la fin de son chargement
     */
    public static void registerAll(Plugin owner, PluginManager manager, ItemProviders items, Logger logger,
                                   Runnable onItemsLoaded, List<String> loaded) {
        for (Candidate candidate : CANDIDATES) {
            Plugin plugin = manager.getPlugin(candidate.spec().pluginName());
            if (plugin == null || !plugin.isEnabled()) {
                continue;
            }
            ClassLoader loader = plugin.getClass().getClassLoader();
            Optional<ItemProvider> provider = candidate.factory().apply(loader);
            if (provider.isEmpty()) {
                logger.warning(
                        ConsoleText.tr("Items custom : {0} est installe mais son API n'est pas reconnue (version non prise en charge) : ses items ne sont pas utilisables.",
                                candidate.spec().pluginName()));
                ComponentEvents.fire(manager, ComponentStateEvent.Kind.ITEM_PROVIDER, candidate.spec().pluginName(),
                        false, "API non reconnue");
                continue;
            }
            items.register(provider.get());
            loaded.add(candidate.spec().pluginName() + " " + plugin.getDescription().getVersion() + " -> "
                    + candidate.spec().namespace() + ":");
            logger.info(ConsoleText.tr("Items custom : {0} pris en charge (namespace {1})",
                    candidate.spec().pluginName(), candidate.spec().namespace()));
            ComponentEvents.fire(manager, ComponentStateEvent.Kind.ITEM_PROVIDER, candidate.spec().pluginName(), true,
                    candidate.spec().namespace());
            if (!candidate.spec().loadedEventClass().isEmpty()) {
                hookLoadedEvent(owner, manager, candidate.spec(), loader, logger, onItemsLoaded);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void hookLoadedEvent(Plugin owner, PluginManager manager, CustomItemPlugins.Spec spec,
                                        ClassLoader loader, Logger logger, Runnable onItemsLoaded) {
        try {
            Class<?> type = Class.forName(spec.loadedEventClass(), false, loader);
            if (!Event.class.isAssignableFrom(type)) {
                return;
            }
            manager.registerEvent((Class<? extends Event>) type, new Listener() { }, EventPriority.MONITOR,
                    (listener, event) -> onItemsLoaded.run(), owner);
        } catch (ClassNotFoundException | LinkageError | RuntimeException absent) {
            logger.log(Level.INFO,
                    ConsoleText.tr("Items custom : evenement de chargement de {0} introuvable, le catalogue ne sera pas reevalue automatiquement (/novabank reload)",
                            spec.pluginName()));
        }
    }
}
