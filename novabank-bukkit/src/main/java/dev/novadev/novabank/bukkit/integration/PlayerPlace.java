package dev.novadev.novabank.bukkit.integration;

import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Position d'un joueur connecte, pour les preferences d'entite des fournisseurs (thread du joueur). */
final class PlayerPlace {

    private PlayerPlace() {
    }

    /**
     * @param playerId joueur
     * @return sa position, vide s'il n'est pas connecte
     */
    static Optional<Location> of(UUID playerId) {
        return Optional.ofNullable(Bukkit.getPlayer(playerId)).map(Player::getLocation);
    }
}
