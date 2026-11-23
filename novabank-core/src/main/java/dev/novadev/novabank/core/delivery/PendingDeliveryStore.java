package dev.novadev.novabank.core.delivery;

import dev.novadev.novabank.api.OperationId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port : file persistante des livraisons en attente.
 *
 * <p>Une livraison est identifiee par son operation d'origine (retrait, ou depot refuse
 * dont les items n'ont pas pu etre rendus). Une livraison entierement remise reste connue
 * du stockage : un nouvel {@link #add} pour la meme operation reste sans effet, ce qui
 * empeche toute double remise si un retrait est rejoue apres coup. Appele uniquement sur
 * l'executeur d'entrees/sorties.
 */
public interface PendingDeliveryStore {

    /**
     * Enregistre une livraison, de facon idempotente : si une livraison existe deja pour
     * le meme retrait, rien ne change.
     *
     * @param delivery livraison a enregistrer
     */
    void add(PendingDelivery delivery);

    /**
     * @param operationId operation d'origine
     * @return la livraison avec la quantite restant due, ou vide si elle est inconnue ou
     *         entierement remise
     */
    Optional<PendingDelivery> find(OperationId operationId);

    /**
     * @param playerId joueur destinataire
     * @return ses livraisons non entierement remises, avec la quantite restant due, par
     *         ordre d'enregistrement
     */
    List<PendingDelivery> pendingFor(UUID playerId);

    /**
     * Enregistre la remise d'une partie (ou de la totalite) d'une livraison.
     *
     * @param operationId operation d'origine
     * @param quantity    unites remises, entre 1 et la quantite restant due
     * @return {@code true} si la remise est enregistree ; {@code false} sans aucun effet si
     *         la livraison est inconnue ou si la quantite depasse le reste du
     * @throws IllegalArgumentException si la quantite n'est pas positive
     */
    boolean markDelivered(OperationId operationId, long quantity);
}
