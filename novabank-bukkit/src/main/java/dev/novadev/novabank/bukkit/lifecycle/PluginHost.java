package dev.novadev.novabank.bukkit.lifecycle;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Logger;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Ce que le moteur NovaBank attend de ce qui le charge : le plugin autonome, un module SuperiorSkyblock2 ou un addon
 * BentoBox. Le moteur ne depend ainsi d'aucun {@link JavaPlugin} : un module ou un addon n'en est pas un.
 */
public interface PluginHost {

    /** @return plugin Bukkit proprietaire des taches, ecouteurs, commandes et services (NovaBank, ou l'hote qui le charge) */
    Plugin owner();

    /** @return journal de NovaBank */
    Logger logger();

    /**
     * @return dossier de donnees de NovaBank (config.yml, lang/, base SQLite...), choisi par {@link DataFolders}
     */
    Path dataFolder();

    /**
     * @param name chemin d'une ressource du JAR NovaBank
     * @return son contenu, ou {@code null} si elle est absente
     */
    InputStream resource(String name);

    /** @return chargeur des classes NovaBank (pilotes de base, planificateur de la plateforme) */
    ClassLoader classLoader();

    /** @return version de NovaBank affichee */
    String version();

    /** @return auteurs de NovaBank */
    List<String> authors();

    /** @return prefixe des commandes dans la table de Bukkit ({@code novabank:bank}) */
    String commandPrefix();

    /**
     * @param plugin plugin autonome NovaBank
     * @return l'hote correspondant
     */
    static PluginHost of(JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        return new PluginHost() {
            @Override
            public Plugin owner() {
                return plugin;
            }

            @Override
            public Logger logger() {
                return plugin.getLogger();
            }

            @Override
            public Path dataFolder() {
                return DataFolders.locate(plugin);
            }

            @Override
            public InputStream resource(String name) {
                return plugin.getResource(name);
            }

            @Override
            public ClassLoader classLoader() {
                return plugin.getClass().getClassLoader();
            }

            @Override
            public String version() {
                return plugin.getDescription().getVersion();
            }

            @Override
            public List<String> authors() {
                return plugin.getDescription().getAuthors();
            }

            @Override
            public String commandPrefix() {
                return plugin.getName().toLowerCase(Locale.ROOT);
            }
        };
    }
}
