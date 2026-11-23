package dev.novadev.novabank.bukkit.gui;

/**
 * Nature d'un clic dans la GUI, deja ramenee aux cas utiles.
 */
public enum MenuClick {
    /** Clic gauche simple. */
    LEFT,
    /** Clic droit simple. */
    RIGHT,
    /** Clic avec majuscule (gauche ou droit). */
    SHIFT,
    /** Tout autre clic (touche numerique, jet, double clic...) : aucune action. */
    OTHER
}
