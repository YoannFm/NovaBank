package dev.novadev.novabank.core.delivery;

/**
 * Politique appliquee aux unites d'un retrait reserve qui n'ont pas pu etre livrees
 * (decision D13).
 */
public enum UndeliveredPolicy {

    /** Re-credit exact a la banque (stock, unites comptees et valeur du retrait). */
    RETURN_TO_BANK,

    /** Livraison differee persistante, remise au joueur plus tard, sans re-credit. */
    PENDING_DELIVERY
}
