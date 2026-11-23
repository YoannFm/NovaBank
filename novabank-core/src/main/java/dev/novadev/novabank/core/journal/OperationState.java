package dev.novadev.novabank.core.journal;

/**
 * Etat d'une operation journalisee.
 *
 * <p>Depots et resets sont {@link #COMMITTED} des leur enregistrement. Un retrait passe
 * par {@link #RESERVED} puis par {@link #COMMITTED} (livre, eventuellement en partie)
 * ou {@link #RETURNED} (entierement rendu a la banque).
 */
public enum OperationState {

    /** Retrait debite de la banque, livraison en cours. */
    RESERVED,

    /** Operation definitive. */
    COMMITTED,

    /** Retrait annule, integralement rendu a la banque. */
    RETURNED
}
