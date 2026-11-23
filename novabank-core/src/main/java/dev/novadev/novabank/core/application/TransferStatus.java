package dev.novadev.novabank.core.application;

/**
 * Issue d'un depot ou d'un retrait demande par un joueur, pour l'affichage.
 */
public enum TransferStatus {

    /** Operation validee en base. */
    SUCCESS,
    /** Une autre operation du joueur est deja en cours. */
    BUSY,
    /** Delai minimal entre deux operations non ecoule. */
    COOLDOWN,
    /** Permission manquante. */
    NO_PERMISSION,
    /** Le joueur n'appartient a aucune banque. */
    NO_BANK,
    /** Le role du joueur dans l'entite ne permet pas cette operation. */
    ROLE_NOT_ALLOWED,
    /** Item non accepte par la banque. */
    NOT_ACCEPTED,
    /** Aucun item identifie en main. */
    NOTHING_IN_HAND,
    /** Aucun item accepte dans l'inventaire. */
    NOTHING_TO_DEPOSIT,
    /** Quantite superieure a la limite par operation. */
    QUANTITY_LIMIT,
    /** Pas assez d'items identifies dans l'inventaire. */
    NOT_ENOUGH_ITEMS,
    /** Stock de la banque insuffisant. */
    INSUFFICIENT_STOCK,
    /** Le joueur s'est deconnecte avant que l'operation commence. */
    PLAYER_OFFLINE,
    /**
     * Arret de NovaBank en cours : operation refusee, ou interrompue avant que des items
     * aient quitte l'inventaire.
     */
    SHUTTING_DOWN,
    /** Maintenance de NovaBank (migration du stockage) : operation refusee. */
    MAINTENANCE,
    /** Base de donnees indisponible : rien n'a ete enregistre, les items sont rendus. */
    STORAGE_ERROR,
    /**
     * Retrait remis au joueur mais confirmation non enregistree en base : le retrait reste
     * reserve et sera solde au prochain demarrage.
     */
    CONFIRMATION_PENDING,
    /** Refus de la banque pour un autre motif (voir le statut de la banque). */
    REFUSED,
    /** Erreur inattendue (journalisee). */
    INTERNAL_ERROR
}
