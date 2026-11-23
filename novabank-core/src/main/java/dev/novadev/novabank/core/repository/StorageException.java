package dev.novadev.novabank.core.repository;

/**
 * Panne technique du stockage (base injoignable, disque plein, schema incompatible...).
 *
 * <p>Levee par les implementations des ports de persistance a la place des exceptions
 * propres a chaque backend. Quand elle est levee pendant une ecriture, aucun etat partiel
 * n'est visible : l'unite atomique a ete annulee, ou, si la panne a coupe sa validation, son
 * issue est inconnue mais entiere (appliquee ou non) ; rejouer le meme identifiant d'operation
 * la tranche (rejeu detecte par le journal). La cause d'origine est toujours conservee.
 */
public class StorageException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param message description de la panne, en francais
     * @param cause   erreur d'origine du backend
     */
    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * @param message description de la panne, en francais
     */
    public StorageException(String message) {
        super(message);
    }
}
