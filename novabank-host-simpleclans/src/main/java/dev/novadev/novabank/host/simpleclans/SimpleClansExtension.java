package dev.novadev.novabank.host.simpleclans;

import dev.novadev.novabank.bukkit.host.EditionExtension;
import dev.novadev.novabank.bukkit.host.HostLabels;
import dev.novadev.novabank.config.model.CommandLabel;
import java.util.ArrayList;
import java.util.List;
import net.sacredlabyrinth.phaed.simpleclans.SimpleClans;
import net.sacredlabyrinth.phaed.simpleclans.commands.SCCommandManager;
import org.bukkit.plugin.Plugin;

/**
 * NovaBank pour SimpleClans : sous-commande {@code /clan <nom>} ({@code commands.hosts.simpleclans}, {@code coffre} par
 * defaut car {@code /clan bank} est la banque d'argent de SimpleClans), ajoutee a la commande {@code %clan} de
 * SimpleClans par son gestionnaire ACF. Droits : permissions {@code novabank.clan.deposit|withdraw}, sinon chef et
 * membres de confiance retirent (regle banks.roles).
 *
 * <p>API VERIFIEE avec SimpleClans 2.19.2 (javap) : {@code SimpleClans.getInstance().getCommandManager()}
 * ({@code SCCommandManager} : ACF relocalise), {@code getCommandReplacements().addReplacement},
 * {@code registerCommand(BaseCommand)}, {@code unregisterCommand(BaseCommand)}.
 */
public final class SimpleClansExtension implements EditionExtension {

    static final String EDITION = "simpleclans";
    /** Remplacement ACF : nom et alias de la sous-commande ({@code coffre|items}). */
    static final String REPLACEMENT = "novabank";

    @Override
    public AutoCloseable start(Plugin plugin) {
        SCCommandManager manager = SimpleClans.getInstance().getCommandManager();
        CommandLabel label = HostLabels.read(plugin, EDITION);
        List<String> names = new ArrayList<>(List.of(label.name()));
        names.addAll(label.aliases());
        manager.getCommandReplacements().addReplacement(REPLACEMENT, String.join("|", names));
        manager.getCommandReplacements().addReplacement(REPLACEMENT + "_description",
                HostLabels.text(plugin, "bank.clan-description", "NovaBank"));
        BankCommand command = new BankCommand(plugin, label.name());
        manager.registerCommand(command);
        return () -> manager.unregisterCommand(command);
    }
}
