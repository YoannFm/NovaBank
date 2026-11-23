package dev.novadev.novabank.bukkit.host;

import dev.novadev.novabank.bukkit.lifecycle.NovaBankBootstrap;
import dev.novadev.novabank.bukkit.lifecycle.PluginHost;
import dev.novadev.novabank.core.text.ConsoleText;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.logging.Level;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicesManager;

/**
 * Demarrage commun a tous les JAR NovaBank (plugin autonome, extensions) : annonce l'edition du JAR, puis rejoint le
 * moteur deja en marche, ou le demarre. Un seul moteur par serveur : une seule configuration, une seule base.
 */
public final class NovaBankLauncher implements AutoCloseable {

    /** Ressource du JAR qui donne son edition ; absente = plugin autonome. */
    public static final String EDITION_RESOURCE = "novabank-edition.txt";

    private final AutoCloseable announcement;
    private final NovaBankBootstrap engine;
    private final List<AutoCloseable> extensions = new ArrayList<>();

    private NovaBankLauncher(AutoCloseable announcement, NovaBankBootstrap engine) {
        this.announcement = announcement;
        this.engine = engine;
    }

    /**
     * Chargement du plugin (avant l'activation) : etape {@link EditionExtension#load} des extensions de ce JAR.
     *
     * @param host ce qui charge ce JAR
     */
    public static void load(PluginHost host) {
        consoleLanguage(host);
        for (EditionExtension extension : ServiceLoader.load(EditionExtension.class, host.classLoader())) {
            try {
                extension.load(host.owner());
            } catch (Exception | LinkageError failure) {
                host.logger().log(Level.WARNING,
                        ConsoleText.tr("Extension {0} : chargement impossible (API de l'hote non reconnue).",
                                extension.getClass().getSimpleName()), failure);
            }
        }
    }

    /** Demarre les extensions de l'edition livrees dans ce JAR ({@link EditionExtension}). */
    private NovaBankLauncher withExtensions(PluginHost host) {
        for (EditionExtension extension : ServiceLoader.load(EditionExtension.class, host.classLoader())) {
            try {
                extensions.add(extension.start(host.owner()));
            } catch (Exception | LinkageError failure) {
                host.logger().log(Level.WARNING,
                        ConsoleText.tr("Extension {0} indisponible (API de l'hote non reconnue) ; la banque reste accessible par /bank.",
                                extension.getClass().getSimpleName()), failure);
            }
        }
        return this;
    }

    /**
     * @param host ce qui charge ce JAR
     * @return l'edition du JAR ({@value #EDITION_RESOURCE}), {@link HostIntegrations#STANDALONE} par defaut
     */
    public static String edition(PluginHost host) {
        try (InputStream input = host.resource(EDITION_RESOURCE)) {
            if (input == null) {
                return HostIntegrations.STANDALONE;
            }
            String edition = new String(input.readAllBytes(), StandardCharsets.UTF_8).trim().toLowerCase(Locale.ROOT);
            return edition.isEmpty() ? HostIntegrations.STANDALONE : edition;
        } catch (IOException unreadable) {
            return HostIntegrations.STANDALONE;
        }
    }

    /**
     * @param host      ce qui charge ce JAR
     * @param pluginJar {@code true} pour un plugin Bukkit, {@code false} pour un module ou un addon d'hote
     * @return le lancement, ou {@code null} si le moteur n'a pas pu demarrer (le JAR doit alors se desactiver)
     */
    public static NovaBankLauncher start(PluginHost host, boolean pluginJar) {
        Objects.requireNonNull(host, "host");
        consoleLanguage(host);
        Plugin owner = host.owner();
        ServicesManager services = owner.getServer().getServicesManager();
        String edition = edition(host);
        AutoCloseable announcement = EditionChannel.announceEdition(services, owner, edition, host.version());
        if (EditionChannel.engineRunning(services)) {
            host.logger().info(
                    ConsoleText.tr("NovaBank ({0}) : moteur NovaBank deja en marche sur ce serveur, edition ajoutee (configuration et base communes dans plugins/NovaBank/).",
                            edition));
            return new NovaBankLauncher(announcement, null).withExtensions(host);
        }
        if (!pluginJar && novaBankPluginInstalled(owner.getServer().getPluginManager(), owner)) {
            host.logger().info(
                    ConsoleText.tr("NovaBank ({0}) : le moteur sera demarre par le plugin NovaBank installe, edition ajoutee.",
                            edition));
            return new NovaBankLauncher(announcement, null).withExtensions(host);
        }
        AutoCloseable permissions = PermissionDeclarations.declare(host, owner.getServer().getPluginManager());
        AutoCloseable announcements = () -> {
            permissions.close();
            announcement.close();
        };
        NovaBankBootstrap engine = new NovaBankBootstrap(host);
        if (!engine.start()) {
            new NovaBankLauncher(announcements, null).close();
            return null;
        }
        return new NovaBankLauncher(announcements, engine).withExtensions(host);
    }

    /**
     * Un plugin NovaBank (autonome ou extension Towny, Lands...) voit tous les hotes : le moteur y tourne de
     * preference plutot que dans un module ou un addon, qui ne voit que le sien.
     */
    private static boolean novaBankPluginInstalled(PluginManager plugins, Plugin self) {
        for (Plugin plugin : plugins.getPlugins()) {
            if (plugin != self && plugin.getDescription().getMain().startsWith("dev.novadev.novabank.")) {
                return true;
            }
        }
        return false;
    }

    /** @return {@code true} si ce JAR fait tourner le moteur */
    public boolean runsEngine() {
        return engine != null;
    }

    @Override
    public void close() {
        for (AutoCloseable extension : extensions) {
            try {
                extension.close();
            } catch (Exception ignored) {
                // L'hote s'arrete de toute facon avec le serveur.
            }
        }
        extensions.clear();
        if (engine != null) {
            engine.close();
        }
        try {
            announcement.close();
        } catch (Exception ignored) {
            // Le service est retire de toute facon a la desactivation du plugin proprietaire.
        }
    }

    /** Langue de la console des le premier message, lue dans {@code plugins/NovaBank/config.yml} (francais s'il manque). */
    private static void consoleLanguage(PluginHost host) {
        java.io.File config = host.dataFolder().resolve("config.yml").toFile();
        ConsoleText.language(org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(config)
                .getString("language", "fr"));
    }
}
