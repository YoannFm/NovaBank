package dev.novadev.novabank.host.simpleclans;

import dev.novadev.novabank.bukkit.host.RemoteBridges;
import net.sacredlabyrinth.phaed.simpleclans.acf.BaseCommand;
import net.sacredlabyrinth.phaed.simpleclans.acf.annotation.CommandAlias;
import net.sacredlabyrinth.phaed.simpleclans.acf.annotation.CommandCompletion;
import net.sacredlabyrinth.phaed.simpleclans.acf.annotation.Description;
import net.sacredlabyrinth.phaed.simpleclans.acf.annotation.Subcommand;
import org.bukkit.entity.Player;
import dev.novadev.novabank.bukkit.host.HostLabels;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicesManager;

/** {@code /clan <nom>}, transmis a la commande de banque publiee par le moteur NovaBank. */
@CommandAlias("%clan")
public final class BankCommand extends BaseCommand {

    private final transient Plugin plugin;
    private final transient ServicesManager services;
    private final String name;

    BankCommand(Plugin plugin, String name) {
        this.plugin = plugin;
        this.services = plugin.getServer().getServicesManager();
        this.name = name;
    }

    /**
     * @param player joueur
     * @param args   arguments apres le nom de la sous-commande
     */
    @Subcommand("%novabank")
    @Description("%novabank_description")
    @CommandCompletion("deposit|withdraw|info|top|open|help")
    public void bank(Player player, String[] args) {
        RemoteBridges.commands(services, SimpleClansExtension.EDITION).ifPresentOrElse(
                commands -> commands.onCommand(player, null, "clan " + name, args),
                () -> player.sendMessage(HostLabels.chat(plugin, "general.not-ready", "NovaBank...")));
    }
}
