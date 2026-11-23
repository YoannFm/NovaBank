package dev.novadev.novabank.core.application;

import java.util.UUID;

/**
 * Port : permissions d'un joueur connecte, verifiees sur le thread du joueur a chaque
 * operation (jamais d'apres l'interface).
 */
@FunctionalInterface
public interface PermissionChecker {

    /**
     * @param playerId   joueur connecte
     * @param permission permission (voir {@link dev.novadev.novabank.core.access.Permissions})
     * @return {@code true} si le joueur la possede
     */
    boolean has(UUID playerId, String permission);
}
