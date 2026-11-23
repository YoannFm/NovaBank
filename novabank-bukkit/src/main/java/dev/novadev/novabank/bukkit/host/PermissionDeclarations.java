package dev.novadev.novabank.bukkit.host;

import dev.novadev.novabank.bukkit.lifecycle.PluginHost;
import dev.novadev.novabank.core.text.ConsoleText;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;

/**
 * Declare les permissions NovaBank que Bukkit ne connait pas encore. Un plugin les tient de son {@code plugin.yml} ;
 * un module SuperiorSkyblock2 ou un addon BentoBox n'en a pas (son descripteur s'appelle
 * {@value EmbeddedHost#DESCRIPTOR}) : sans cela, une permission non declaree serait reservee aux op et les joueurs
 * n'auraient pas acces a la banque.
 */
final class PermissionDeclarations {

    private PermissionDeclarations() {
    }

    private static InputStream descriptor(PluginHost host) {
        InputStream embedded = host.resource(EmbeddedHost.DESCRIPTOR);
        return embedded != null ? embedded : host.resource("plugin.yml");
    }

    /**
     * @param host    ce qui charge NovaBank (son {@code plugin.yml} embarque donne les permissions)
     * @param plugins gestionnaire de plugins
     * @return le retrait des permissions ajoutees ici
     */
    static AutoCloseable declare(PluginHost host, PluginManager plugins) {
        List<Permission> added = new ArrayList<>();
        try (InputStream input = descriptor(host)) {
            if (input == null) {
                return () -> { };
            }
            YamlConfiguration descriptor = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(input, StandardCharsets.UTF_8));
            ConfigurationSection permissions = descriptor.getConfigurationSection("permissions");
            if (permissions != null) {
                for (String name : permissions.getKeys(false)) {
                    if (plugins.getPermission(name) == null) {
                        PermissionDefault byDefault = PermissionDefault.getByName(
                                permissions.getString(name + ".default", "op"));
                        Permission permission = new Permission(name, permissions.getString(name + ".description", ""),
                                byDefault == null ? PermissionDefault.OP : byDefault);
                        plugins.addPermission(permission);
                        added.add(permission);
                    }
                }
            }
        } catch (IOException unreadable) {
            host.logger().warning(ConsoleText.tr("Permissions NovaBank non declarees : {0}", unreadable.getMessage()));
        }
        return () -> added.forEach(plugins::removePermission);
    }
}
