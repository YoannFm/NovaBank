package dev.novadev.novabank.bukkit.lifecycle;

/**
 * Refus de demarrage attendu (environnement ou configuration non supportes) : le message
 * est destine a l'administrateur, sans trace de pile.
 */
public final class StartupException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param message explication pour l'administrateur
     */
    public StartupException(String message) {
        super(message);
    }
}
