package dev.novadev.novabank.storage.file;

/**
 * Donnee illisible ou incoherente dans un fichier du stockage (le message cite le fichier et le
 * champ). Jamais ignoree : l'ouverture du stockage est refusee.
 */
final class CorruptDataException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    CorruptDataException(String message) {
        super(message);
    }

    CorruptDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
