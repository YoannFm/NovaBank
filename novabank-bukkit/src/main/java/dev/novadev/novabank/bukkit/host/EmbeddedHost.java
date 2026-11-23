package dev.novadev.novabank.bukkit.host;

import dev.novadev.novabank.bukkit.lifecycle.PluginHost;
import dev.novadev.novabank.core.text.ConsoleText;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

/**
 * NovaBank charge par un plugin hote (module SuperiorSkyblock2, addon BentoBox) : les taches, ecouteurs et commandes
 * appartiennent a l'hote, les donnees restent dans {@code plugins/NovaBank/}. Version et auteurs sont lus dans le
 * descripteur embarque {@value #DESCRIPTOR}.
 */
public final class EmbeddedHost implements PluginHost {

    /**
     * Descripteur NovaBank d'un module ou d'un addon : le {@code plugin.yml} du moteur sous un autre nom, pour que
     * BentoBox ne prenne pas l'addon pour un plugin.
     */
    public static final String DESCRIPTOR = "novabank-plugin.yml";

    private final Plugin owner;
    private final Logger logger;
    private final Function<String, InputStream> resources;
    private final ClassLoader loader;
    private final YamlConfiguration descriptor;

    private EmbeddedHost(Plugin owner, Logger logger, Function<String, InputStream> resources, ClassLoader loader) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.resources = Objects.requireNonNull(resources, "resources");
        this.loader = Objects.requireNonNull(loader, "loader");
        this.descriptor = new YamlConfiguration();
        try (InputStream input = resources.apply(DESCRIPTOR)) {
            if (input != null) {
                descriptor.load(new InputStreamReader(input, StandardCharsets.UTF_8));
            }
        } catch (IOException | org.bukkit.configuration.InvalidConfigurationException unreadable) {
            logger.warning(ConsoleText.tr("{0} illisible : version inconnue ({1})",
                    DESCRIPTOR, unreadable.getMessage()));
        }
    }

    /**
     * @param owner     plugin hote, proprietaire des taches et ecouteurs
     * @param logger    journal du module ou de l'addon
     * @param resources lecture d'une ressource du JAR NovaBank
     * @param loader    chargeur des classes NovaBank
     * @return l'hote
     */
    public static EmbeddedHost of(Plugin owner, Logger logger, Function<String, InputStream> resources,
                                  ClassLoader loader) {
        return new EmbeddedHost(owner, logger, resources, loader);
    }

    @Override
    public Plugin owner() {
        return owner;
    }

    @Override
    public Logger logger() {
        return logger;
    }

    @Override
    public Path dataFolder() {
        return owner.getDataFolder().toPath().resolveSibling(DATA_FOLDER);
    }

    @Override
    public InputStream resource(String name) {
        return resources.apply(name);
    }

    @Override
    public ClassLoader classLoader() {
        return loader;
    }

    @Override
    public String version() {
        return descriptor.getString("version", "inconnue");
    }

    @Override
    public List<String> authors() {
        String author = descriptor.getString("author");
        return author == null ? List.of() : List.of(author);
    }

    @Override
    public String commandPrefix() {
        return "novabank";
    }
}
