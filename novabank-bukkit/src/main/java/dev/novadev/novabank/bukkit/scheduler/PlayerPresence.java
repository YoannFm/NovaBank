package dev.novadev.novabank.bukkit.scheduler;

import java.util.UUID;

/**
 * Indique si un joueur est connecte a ce serveur, a interroger depuis le thread qui
 * manipulera le joueur.
 */
@FunctionalInterface
public interface PlayerPresence {

    /**
     * @param playerId joueur
     * @return {@code true} s'il est connecte
     */
    boolean isOnline(UUID playerId);
}
