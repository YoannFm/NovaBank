package dev.novadev.novabank.bukkit.integration;

import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.operation.OperationType;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.bukkit.entity.Player;

/**
 * Droits d'un hote donnes par permission (Towny les attribue par rang dans {@code townyperms.yml}) :
 * {@code novabank.<type>.deposit} et {@code novabank.<type>.withdraw}. Une permission ni donnee ni retiree au joueur
 * ne decide rien : la regle {@code banks.roles} s'applique.
 */
final class PermissionRights implements HostBridge.Rights {

    private final String prefix;
    private final Function<UUID, Player> players;

    /**
     * @param entityType type de banque ({@code town})
     * @param players    joueur connecte
     */
    PermissionRights(String entityType, Function<UUID, Player> players) {
        this.prefix = "novabank." + entityType + ".";
        this.players = players;
    }

    @Override
    public Optional<Boolean> allows(UUID playerId, EntityRef entity, OperationType type) {
        Player player = players.apply(playerId);
        String node = prefix + type.name().toLowerCase(Locale.ROOT);
        return player == null || !player.isPermissionSet(node) ? Optional.empty() : Optional.of(player.hasPermission(node));
    }
}
