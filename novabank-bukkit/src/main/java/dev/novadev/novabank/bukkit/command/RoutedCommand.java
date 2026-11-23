package dev.novadev.novabank.bukkit.command;

import java.util.List;
import java.util.Objects;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.plugin.Plugin;

/**
 * Commande Bukkit mince, enregistree dynamiquement (nom et alias configurables) : tout
 * est delegue a un {@link CommandRouter} testable sans serveur.
 */
public final class RoutedCommand extends Command implements PluginIdentifiableCommand {

    private final Plugin plugin;
    private final CommandRouter router;

    /**
     * @param name        nom de la commande
     * @param aliases     alias
     * @param description description (aide du serveur)
     * @param permission  permission requise pour voir et utiliser la commande
     * @param plugin      plugin proprietaire
     * @param router      logique de la commande
     */
    public RoutedCommand(String name, List<String> aliases, String description, String permission, Plugin plugin,
                         CommandRouter router) {
        super(name, description, "/" + name, List.copyOf(aliases));
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.router = Objects.requireNonNull(router, "router");
        setPermission(permission);
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        router.execute(new SenderActor(sender), commandLabel, args);
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        return router.complete(new SenderActor(sender), args);
    }

    @Override
    public Plugin getPlugin() {
        return plugin;
    }
}
