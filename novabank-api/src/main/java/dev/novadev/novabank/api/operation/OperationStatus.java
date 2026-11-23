package dev.novadev.novabank.api.operation;

/**
 * Issue d'une operation bancaire.
 *
 * <p>Seuls {@link #SUCCESS} et {@link #DUPLICATE_OPERATION} signifient que l'effet de
 * l'operation est acquis ; tous les autres statuts sont des refus sans aucun effet.
 */
public enum OperationStatus {

    /** L'operation vient d'etre appliquee. */
    SUCCESS,

    /**
     * L'operation avait deja ete appliquee avec le meme identifiant : rien n'a ete
     * refait, le resultat decrit l'application d'origine.
     */
    DUPLICATE_OPERATION,

    /** L'item n'est pas accepte par la banque (absent du catalogue de prix). */
    NOT_ACCEPTED,

    /** Quantite nulle, negative ou incoherente avec l'operation. */
    INVALID_QUANTITY,

    /** La quantite resultante depasserait la capacite d'un stock. */
    QUANTITY_OVERFLOW,

    /** Le stock de la banque est insuffisant pour le retrait demande. */
    INSUFFICIENT_STOCK,

    /** Aucune banque ne porte cet identifiant. */
    UNKNOWN_BANK,

    /** Aucun retrait reserve ne porte cet identifiant d'operation. */
    UNKNOWN_OPERATION,

    /**
     * Le retrait a deja ete solde dans l'autre sens (confirmation d'un retrait annule,
     * ou annulation d'un retrait confirme).
     */
    ALREADY_SETTLED,

    /**
     * Le stockage n'a pas pu appliquer l'operation a cause de modifications
     * concurrentes repetees ; l'operation peut etre retentee avec le meme identifiant.
     */
    CONCURRENT_MODIFICATION
}
