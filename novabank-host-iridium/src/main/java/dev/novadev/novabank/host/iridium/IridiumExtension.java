package dev.novadev.novabank.host.iridium;

import com.iridium.iridiumskyblock.IridiumSkyblock;
import com.iridium.iridiumskyblock.database.Island;
import com.iridium.iridiumskyblock.database.User;
import com.iridium.iridiumskyblock.dependencies.iridiumcore.Item;
import com.iridium.iridiumskyblock.dependencies.iridiumteams.Permission;
import com.iridium.iridiumskyblock.dependencies.xseries.XMaterial;
import dev.novadev.novabank.api.operation.OperationType;
import dev.novadev.novabank.bukkit.host.EditionExtension;
import dev.novadev.novabank.bukkit.host.HostLabels;
import dev.novadev.novabank.bukkit.host.RemoteBridges;
import dev.novadev.novabank.bukkit.lifecycle.DataFolders;
import dev.novadev.novabank.core.text.ConsoleText;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.plugin.Plugin;

/**
 * NovaBank pour IridiumSkyblock : sous-commande {@code /is <nom>} ({@code commands.hosts.iridiumskyblock}) et
 * permissions d'ile {@code novabankDeposit} (membre et plus) et {@code novabankWithdraw} (plus haut rang configure,
 * co-proprietaire), reglables dans le menu des permissions de l'ile. Ces permissions decident toujours.
 *
 * <p>API VERIFIEE avec IridiumSkyblock 4.1.5 (javap) : {@code IridiumTeams.addPermission(String, Permission)},
 * {@code getPermissionList()}, {@code getTeamPermission(Team, User, String)}, {@code getTeamManager().getTeamViaID(int)},
 * {@code getUserManager().getUserByUUID(UUID)}, {@code CommandManager.registerCommand(Command)} et {@code getCommands()}.
 */
public final class IridiumExtension implements EditionExtension {

    static final String EDITION = "iridiumskyblock";
    static final String DEPOSIT = "novabankDeposit";
    static final String WITHDRAW = "novabankWithdraw";
    private static final int MEMBER_RANK = 1;

    @Override
    public AutoCloseable start(Plugin plugin) {
        IridiumSkyblock iridium = IridiumSkyblock.getInstance();
        int topRank = iridium.getConfiguration().userRanks.keySet().stream().max(Integer::compare).orElse(MEMBER_RANK);
        iridium.addPermission(DEPOSIT, permission(XMaterial.CHEST, 10, "&bBanque : deposer", MEMBER_RANK));
        iridium.addPermission(WITHDRAW, permission(XMaterial.HOPPER, 12, "&bBanque : retirer", topRank));

        BankCommand command = new BankCommand(HostLabels.read(plugin, EDITION), plugin);
        String name = command.aliases.get(0);
        if (iridium.getCommandManager().getCommands().stream().anyMatch(other -> other.aliases.stream()
                .anyMatch(name::equalsIgnoreCase))) {
            plugin.getLogger().warning(
                    ConsoleText.tr("IridiumSkyblock a deja /is {0} : changez commands.hosts.iridiumskyblock.name dans {1}.",
                            name, DataFolders.locate(plugin).resolve("config.yml")));
        }
        iridium.getCommandManager().registerCommand(command);
        AutoCloseable rights = RemoteBridges.publishRights(plugin.getServer().getServicesManager(), plugin, EDITION,
                query -> allows(iridium, query));
        return () -> {
            rights.close();
            iridium.getCommandManager().getCommands().remove(command);
            Map<String, Permission> permissions = iridium.getPermissionList();
            permissions.remove(DEPOSIT);
            permissions.remove(WITHDRAW);
        };
    }

    private static Permission permission(XMaterial icon, int slot, String name, int defaultRank) {
        return new Permission(new Item(icon, slot, 1, name, List.of("&7NovaBank : rang minimal")), 2, defaultRank);
    }

    /** Requete {@code "<joueur>;<id de l'ile>;DEPOSIT|WITHDRAW"}. */
    private static Optional<Boolean> allows(IridiumSkyblock iridium, String query) {
        String[] parts = query.split(";", 3);
        if (parts.length != 3) {
            return Optional.empty();
        }
        String key = OperationType.DEPOSIT.name().equals(parts[2]) ? DEPOSIT : WITHDRAW;
        Optional<User> user = iridium.getUserManager().getUserByUUID(UUID.fromString(parts[0]));
        Optional<Island> island = iridium.getTeamManager().getTeamViaID(Integer.parseInt(parts[1]));
        if (user.isEmpty() || island.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(iridium.getTeamManager().getTeamPermission(island.get(), user.get(), key));
    }
}
