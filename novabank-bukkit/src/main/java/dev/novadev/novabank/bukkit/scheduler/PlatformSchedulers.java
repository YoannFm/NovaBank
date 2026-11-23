package dev.novadev.novabank.bukkit.scheduler;

import dev.novadev.novabank.bukkit.platform.ServerPlatform;
import dev.novadev.novabank.core.text.ConsoleText;
import java.lang.reflect.InvocationTargetException;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.Server;
import org.bukkit.entity.Player;

/**
 * Choix du planificateur selon la plateforme detectee. Folia n'a pas de planificateur Bukkit
 * (il leve {@link UnsupportedOperationException}) : son implementation, compilee contre
 * l'API Folia dans un module separe, est chargee par son nom UNIQUEMENT sous Folia, si bien
 * qu'aucune classe de l'API Folia n'est jamais chargee sous Spigot/Paper.
 */
public final class PlatformSchedulers {

    /** Implementation Folia, dans le module {@code novabank-platform-folia} (meme JAR). */
    public static final String FOLIA_FACTORY = "dev.novadev.novabank.platform.folia.FoliaSchedulerFactory";

    private PlatformSchedulers() {
    }

    /**
     * @param platform plateforme detectee
     * @param loader   chargeur des classes du plugin
     * @return la fabrique du planificateur de cette plateforme
     * @throws IllegalStateException si l'implementation Folia est absente ou invalide
     */
    public static SchedulerFactory forPlatform(ServerPlatform platform, ClassLoader loader) {
        return forPlatform(platform, FOLIA_FACTORY, loader);
    }

    static SchedulerFactory forPlatform(ServerPlatform platform, String foliaFactory, ClassLoader loader) {
        Objects.requireNonNull(platform, "platform");
        Objects.requireNonNull(foliaFactory, "foliaFactory");
        Objects.requireNonNull(loader, "loader");
        if (platform == ServerPlatform.FOLIA) {
            return load(foliaFactory, loader);
        }
        return (plugin, io, logger) -> {
            Server server = plugin.getServer();
            return new BukkitTaskScheduler(plugin, server.getScheduler(), io, playerId -> isOnline(server, playerId),
                    logger);
        };
    }

    private static SchedulerFactory load(String className, ClassLoader loader) {
        try {
            return Class.forName(className, true, loader).asSubclass(SchedulerFactory.class)
                    .getConstructor().newInstance();
        } catch (ClassNotFoundException | LinkageError | ClassCastException | NoSuchMethodException
                 | InstantiationException | IllegalAccessException | InvocationTargetException failure) {
            throw new IllegalStateException(ConsoleText.tr("Planificateur Folia indisponible ({0}) : {1}",
                    className, failure), failure);
        }
    }

    private static boolean isOnline(Server server, UUID playerId) {
        Player player = server.getPlayer(playerId);
        return player != null && player.isOnline();
    }
}
