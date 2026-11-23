package dev.novadev.novabank.core.migration;

/**
 * Migration refusee ou en echec. La source n'est jamais modifiee par une migration ; le message
 * indique l'etat de la cible.
 */
public final class MigrationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param message cause et etat de la cible, en francais
     */
    public MigrationException(String message) {
        super(message);
    }

    /**
     * @param message cause et etat de la cible, en francais
     * @param cause   erreur d'origine
     */
    public MigrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
