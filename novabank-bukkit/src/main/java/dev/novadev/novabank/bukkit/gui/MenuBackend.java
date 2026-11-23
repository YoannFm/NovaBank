package dev.novadev.novabank.bukkit.gui;

import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.core.application.BankView;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Operations utilisees par la GUI. L'implementation passe par la couche application
 * (memes controles que les commandes) et envoie elle-meme au joueur le resultat de chaque
 * depot ou retrait. Les rappels sont faits sur le thread du joueur.
 */
public interface MenuBackend {

    /**
     * @param playerId joueur
     * @param done     banque active lue en base
     */
    void view(UUID playerId, Consumer<BankView> done);

    /**
     * @param playerId joueur
     * @param item     item
     * @param quantity quantite, positive
     * @param done     appele une fois l'operation terminee (resultat deja envoye)
     */
    void withdraw(UUID playerId, ItemKey item, long quantity, Runnable done);

    /**
     * @param playerId joueur
     * @param done     appele une fois l'operation terminee (resultats deja envoyes)
     */
    void depositAll(UUID playerId, Runnable done);
}
