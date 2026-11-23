package dev.novadev.novabank.bukkit.command;

import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.core.application.Amount;
import dev.novadev.novabank.core.application.ItemChoice;
import java.util.UUID;

/**
 * Actions de la commande des joueurs, apres lecture des arguments.
 */
public interface PlayerActions {

    /**
     * @param actor    emetteur
     * @param playerId joueur
     * @param choice   items a deposer
     * @param amount   quantite par item
     */
    void deposit(CommandActor actor, UUID playerId, ItemChoice choice, Amount amount);

    /**
     * @param actor    emetteur
     * @param playerId joueur
     * @param item     item a retirer
     * @param amount   quantite
     */
    void withdraw(CommandActor actor, UUID playerId, ItemKey item, Amount amount);

    /**
     * @param actor    emetteur
     * @param playerId joueur
     * @param label    commande saisie (pour les indications)
     */
    void info(CommandActor actor, UUID playerId, String label);

    /**
     * @param actor      emetteur
     * @param playerId   joueur
     * @param entityType type de banque choisi
     */
    void select(CommandActor actor, UUID playerId, String entityType);

    /**
     * @param actor    emetteur
     * @param playerId joueur
     */
    void open(CommandActor actor, UUID playerId);

    /**
     * Affiche le classement d'un type de banque.
     *
     * @param actor      destinataire
     * @param playerId   joueur qui consulte
     * @param entityType type de banque, ou {@code null} pour le premier type classe
     */
    void top(CommandActor actor, UUID playerId, String entityType);
}
