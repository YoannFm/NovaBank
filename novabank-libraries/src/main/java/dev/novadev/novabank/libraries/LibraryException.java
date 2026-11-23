package dev.novadev.novabank.libraries;

/**
 * Echec definitif de l'obtention ou du chargement d'une bibliotheque. Le message est
 * destine a l'administrateur du serveur.
 */
public class LibraryException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param message description de l'echec
     */
    public LibraryException(String message) {
        super(message);
    }

    /**
     * @param message description de l'echec
     * @param cause   cause d'origine
     */
    public LibraryException(String message, Throwable cause) {
        super(message, cause);
    }
}
