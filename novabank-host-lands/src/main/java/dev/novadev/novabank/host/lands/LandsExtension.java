package dev.novadev.novabank.host.lands;

import dev.novadev.novabank.api.operation.OperationType;
import dev.novadev.novabank.bukkit.host.EditionExtension;
import dev.novadev.novabank.bukkit.host.RemoteBridges;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import me.angeschossen.lands.api.LandsIntegration;
import me.angeschossen.lands.api.applicationframework.util.ULID;
import me.angeschossen.lands.api.flags.enums.FlagTarget;
import me.angeschossen.lands.api.flags.enums.RoleFlagCategory;
import me.angeschossen.lands.api.flags.type.RoleFlag;
import me.angeschossen.lands.api.land.Land;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * NovaBank pour Lands : flags de role {@code novabank_deposit} (actif par defaut) et {@code novabank_withdraw}
 * (inactif par defaut : a donner aux roles de confiance), reglables dans le menu des roles de chaque terre. Lands
 * n'accepte pas de sous-commande : la banque s'ouvre par {@code /landbank} (commande propre montee par le moteur).
 *
 * <p>API VERIFIEE avec Lands 7.26.3 (javap) : {@code LandsIntegration.of(Plugin)}, {@code getFlagRegistry().register},
 * {@code RoleFlag.of(LandsIntegration, FlagTarget, RoleFlagCategory, String)}, {@code getLandByULID(ULID)},
 * {@code Land.getDefaultArea().hasRoleFlag(UUID, RoleFlag)}.
 */
public final class LandsExtension implements EditionExtension {

    static final String EDITION = "lands";
    static final String DEPOSIT = "novabank_deposit";
    static final String WITHDRAW = "novabank_withdraw";

    /** Lands exige les flags avant de lire ses terres : au chargement du plugin. */
    @Override
    public void load(Plugin plugin) {
        LandsIntegration lands = LandsIntegration.of(plugin);
        register(lands, DEPOSIT, Material.CHEST, "Banque : deposer", "Deposer des items dans la banque de la terre.",
                true);
        register(lands, WITHDRAW, Material.HOPPER, "Banque : retirer", "Retirer des items de la banque de la terre.",
                false);
    }

    private static void register(LandsIntegration lands, String name, Material icon, String title, String description,
                                 boolean byDefault) {
        if (lands.getFlagRegistry().get(name) != null) {
            return;
        }
        RoleFlag flag = RoleFlag.of(lands, FlagTarget.PLAYER, RoleFlagCategory.ACTION, name);
        flag.setDisplayName(title);
        flag.setIcon(new ItemStack(icon));
        flag.setDescription(List.of(description, "NovaBank"));
        flag.setDefaultState(byDefault);
        lands.getFlagRegistry().register(flag);
    }

    @Override
    public AutoCloseable start(Plugin plugin) {
        LandsIntegration lands = LandsIntegration.of(plugin);
        return RemoteBridges.publishRights(plugin.getServer().getServicesManager(), plugin, EDITION,
                query -> allows(lands, query));
    }

    /** Requete {@code "<joueur>;<ULID de la terre>;DEPOSIT|WITHDRAW"} : flag de role du joueur sur la terre. */
    private static Optional<Boolean> allows(LandsIntegration lands, String query) {
        String[] parts = query.split(";", 3);
        if (parts.length != 3) {
            return Optional.empty();
        }
        RoleFlag flag = lands.getFlagRegistry().getRole(OperationType.DEPOSIT.name().equals(parts[2]) ? DEPOSIT : WITHDRAW);
        Land land = lands.getLandByULID(ULID.fromString(parts[1]));
        if (flag == null || land == null) {
            return Optional.empty();
        }
        return Optional.of(land.getDefaultArea().hasRoleFlag(UUID.fromString(parts[0]), flag));
    }
}
