package dev.novadev.novabank.host.iridium;

import com.iridium.iridiumskyblock.database.Island;
import com.iridium.iridiumskyblock.database.User;
import com.iridium.iridiumskyblock.dependencies.iridiumteams.IridiumTeams;
import com.iridium.iridiumskyblock.dependencies.iridiumteams.commands.Command;
import dev.novadev.novabank.bukkit.host.RemoteBridges;
import dev.novadev.novabank.config.model.CommandLabel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bukkit.command.CommandSender;
import dev.novadev.novabank.bukkit.host.HostLabels;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicesManager;

/** {@code /is <nom>} d'IridiumSkyblock, transmis a la commande de banque publiee par le moteur NovaBank. */
final class BankCommand extends Command<Island, User> {

    private final Plugin plugin;
    private final ServicesManager services;

    BankCommand(CommandLabel label, Plugin plugin) {
        super(names(label), HostLabels.text(plugin, "bank.description", "NovaBank"),
                "[deposit|withdraw|info|top|open|help]", "", 0);
        this.plugin = plugin;
        this.services = plugin.getServer().getServicesManager();
    }

    private static List<String> names(CommandLabel label) {
        List<String> names = new ArrayList<>();
        names.add(label.name());
        names.addAll(label.aliases());
        return names;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args, IridiumTeams<Island, User> iridium) {
        String[] typed = withoutName(args);
        return RemoteBridges.commands(services, IridiumExtension.EDITION)
                .map(commands -> commands.onCommand(sender, null, "is " + aliases.get(0), typed))
                .orElseGet(() -> {
                    sender.sendMessage(HostLabels.chat(plugin, "general.not-ready", "NovaBank..."));
                    return false;
                });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args, IridiumTeams<Island, User> iridium) {
        return RemoteBridges.commands(services, IridiumExtension.EDITION)
                .map(commands -> commands.onTabComplete(sender, null, aliases.get(0), withoutName(args)))
                .orElse(List.of());
    }

    /** Iridium peut laisser le nom de la sous-commande en tete des arguments : il est retire. */
    private String[] withoutName(String[] args) {
        return args.length > 0 && aliases.stream().anyMatch(args[0]::equalsIgnoreCase)
                ? Arrays.copyOfRange(args, 1, args.length) : args;
    }
}
