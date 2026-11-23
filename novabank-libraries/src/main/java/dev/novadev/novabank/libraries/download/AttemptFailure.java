package dev.novadev.novabank.libraries.download;

/**
 * Echec d'un essai de telechargement sur un depot.
 */
final class AttemptFailure extends Exception {

    private static final long serialVersionUID = 1L;

    private final boolean retryable;

    /**
     * @param message   cause lisible par un administrateur
     * @param retryable {@code true} si un nouvel essai sur le meme depot peut reussir
     *                  (erreur reseau, delai depasse, erreur serveur 5xx)
     */
    AttemptFailure(String message, boolean retryable) {
        super(message, null, false, false);
        this.retryable = retryable;
    }

    boolean retryable() {
        return retryable;
    }
}
