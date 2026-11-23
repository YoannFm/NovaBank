package dev.novadev.novabank.core.selection;

import dev.novadev.novabank.api.entity.EntityRef;
import java.util.Optional;
import java.util.UUID;

/**
 * Port : banque active choisie par chaque joueur, persistee.
 *
 * <p>Le choix enregistre n'est qu'une preference : il est ignore si le joueur n'appartient
 * plus a l'entite (voir {@link BankSelector}). Appele uniquement sur l'executeur
 * d'entrees/sorties.
 */
public interface ActiveBankStore {

    /**
     * @param playerId joueur
     * @return l'entite choisie, ou vide si le joueur n'a jamais choisi
     */
    Optional<EntityRef> find(UUID playerId);

    /**
     * Enregistre (ou remplace) le choix d'un joueur.
     *
     * @param playerId joueur
     * @param entity   entite choisie
     */
    void save(UUID playerId, EntityRef entity);
}
