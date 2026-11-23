package dev.novadev.novabank.bukkit.lifecycle;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.bukkit.plugin.Plugin;

/**
 * Dossier de donnees de NovaBank (config.yml, lang/, base SQLite...). Chaque JAR a le sien, selon la convention de son
 * hote : {@code plugins/NovaBank/} pour un plugin, {@code plugins/SuperiorSkyblock2/modules/NovaBank/} pour le module,
 * {@code plugins/BentoBox/addons/NovaBank/} pour l'addon. Un seul moteur tourne par serveur : le dossier qui a deja un
 * {@code config.yml} sert donc a tous les JAR, dans l'ordre de {@link #candidates}, pour qu'ajouter ou changer de JAR
 * garde la configuration et la base.
 */
public final class DataFolders {

    /** Nom du dossier de NovaBank dans chaque emplacement. */
    public static final String NAME = "NovaBank";

    private DataFolders() {
    }

    /**
     * @param plugins dossier {@code plugins/} du serveur
     * @param own     dossier du JAR qui demarre le moteur
     * @return le premier emplacement qui a deja un {@code config.yml}, sinon {@code own}
     */
    public static Path locate(Path plugins, Path own) {
        for (Path candidate : candidates(plugins)) {
            if (Files.isRegularFile(candidate.resolve("config.yml"))) {
                return candidate;
            }
        }
        return own;
    }

    /**
     * Pour un JAR qui lit la configuration sans forcement faire tourner le moteur.
     *
     * @param anyPlugin un plugin du serveur (pour trouver {@code plugins/})
     * @return le dossier en service, {@code plugins/NovaBank/} s'il n'y en a pas encore
     */
    public static Path locate(Plugin anyPlugin) {
        Path plugins = anyPlugin.getDataFolder().toPath().getParent();
        return locate(plugins, plugins.resolve(NAME));
    }

    /** @return les emplacements possibles, par priorite */
    static List<Path> candidates(Path plugins) {
        return List.of(plugins.resolve(NAME),
                plugins.resolve("SuperiorSkyblock2").resolve("modules").resolve(NAME),
                plugins.resolve("BentoBox").resolve("addons").resolve(NAME));
    }
}
