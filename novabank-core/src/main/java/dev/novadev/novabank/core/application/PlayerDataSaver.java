package dev.novadev.novabank.core.application;

import java.util.UUID;

/**
 * Port : sauvegarde immediate des donnees d'un joueur connecte (inventaire compris).
 *
 * <p>Appele sur le thread du joueur, juste APRES chaque validation en base (voir
 * {@link BankService}) : un crash du serveur ramene l'inventaire a sa derniere sauvegarde,
 * qui reflete alors deja l'operation.
 */
@FunctionalInterface
public interface PlayerDataSaver {

    /**
     * @param playerId joueur connecte
     */
    void save(UUID playerId);
}
