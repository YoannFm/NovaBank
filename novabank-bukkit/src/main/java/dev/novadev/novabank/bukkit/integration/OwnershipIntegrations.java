package dev.novadev.novabank.bukkit.integration;

import dev.novadev.novabank.api.entity.OwnershipProvider;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.bukkit.plugin.Plugin;

/**
 * Les 8 plugins d'iles et de territoires livres : leur fournisseur d'entites, construit par reflexion. Leur activation
 * (edition installee, plugin active) est faite par {@code HostIntegrations}. Tout autre plugin fournit son propre type
 * par l'API ({@code NovaBankApi.registerOwnershipProvider}).
 */
public final class OwnershipIntegrations {

    /**
     * Ce dont une integration a besoin pour se construire.
     *
     * @param loader      chargeur de classes du plugin hote
     * @param playerNames nom connu d'un joueur
     * @param owner       plugin NovaBank (certaines API exigent le plugin appelant)
     */
    public record Context(ClassLoader loader, Function<UUID, Optional<String>> playerNames, Plugin owner) {
    }

    /**
     * Une integration livree.
     *
     * @param pluginName nom du plugin hote (plugin.yml)
     * @param edition    edition NovaBank qui la couvre, cle de {@code commands.hosts}
     * @param factory    construction du fournisseur ; vide si l'API du plugin n'est pas reconnue
     * @param bridge     pont (commande montee dans l'hote, droits de l'hote), ou {@code null}
     */
    public record Integration(String pluginName, String edition,
                              Function<Context, Optional<OwnershipProvider>> factory, HostBridge bridge) {

        Integration(String pluginName, String edition, Function<Context, Optional<OwnershipProvider>> factory) {
            this(pluginName, edition, factory, null);
        }
    }

    // Ordre = priorite quand deux plugins fournissent le meme type (deux plugins d'iles).
    private static final List<Integration> ALL = List.of(
            new Integration("SuperiorSkyblock2", "superiorskyblock2",
                    c -> SuperiorSkyblockOwnership.create(c.loader(), c.playerNames()), new SuperiorSkyblockBridge()),
            new Integration("BentoBox", "bentobox", c -> BentoBoxOwnership.create(c.loader(), c.playerNames())),
            new Integration("IridiumSkyblock", "iridiumskyblock", c -> IridiumSkyblockOwnership.create(c.loader())),
            new Integration("Towny", "towny", c -> TownyOwnership.create(c.loader()), new TownyBridge()),
            new Integration("SimpleClans", "simpleclans", c -> SimpleClansOwnership.create(c.loader()),
                    new PermissionBridge()),
            new Integration("Lands", "lands", c -> LandsOwnership.create(c.loader(), c.owner()),
                    new TopLevelBridge()),
            new Integration("GriefPrevention", "griefprevention", c -> GriefPreventionOwnership.create(c.loader()),
                    new TopLevelBridge()),
            new Integration("WorldGuard", "worldguard", c -> WorldGuardOwnership.create(c.loader()),
                    new TopLevelBridge()));

    private OwnershipIntegrations() {
    }

    /** @return les integrations livrees, par priorite */
    public static List<Integration> all() {
        return ALL;
    }
}
