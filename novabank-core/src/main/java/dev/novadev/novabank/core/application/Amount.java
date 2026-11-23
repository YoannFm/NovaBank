package dev.novadev.novabank.core.application;

import dev.novadev.novabank.core.text.ConsoleText;

/**
 * Quantite demandee par un joueur : un nombre exact, ou "tout" (borne par la quantite
 * maximale par operation).
 *
 * @param all   {@code true} pour "tout"
 * @param value quantite exacte, positive ; 0 pour "tout"
 */
public record Amount(boolean all, long value) {

    private static final Amount ALL = new Amount(true, 0L);

    /**
     * @throws IllegalArgumentException si une quantite exacte n'est pas positive
     */
    public Amount {
        if (all ? value != 0L : value < 1L) {
            throw new IllegalArgumentException(ConsoleText.tr("Quantite invalide : {0}", value));
        }
    }

    /**
     * @return la quantite "tout"
     */
    public static Amount everything() {
        return ALL;
    }

    /**
     * @param value quantite exacte, positive
     * @return la quantite
     */
    public static Amount of(long value) {
        return new Amount(false, value);
    }

    /**
     * Quantite a deplacer pour un disponible donne.
     *
     * @param available unites disponibles
     * @param max       quantite maximale par operation
     * @return pour "tout", {@code min(available, max)} ; sinon la quantite exacte
     */
    public long resolve(long available, long max) {
        return all ? Math.min(available, max) : value;
    }
}
