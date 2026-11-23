package dev.novadev.novabank.bukkit.gui;

import org.bukkit.event.inventory.ClickType;

/**
 * Ramene les types de clic Bukkit aux cas utiles de la GUI.
 */
final class ClickKinds {

    private ClickKinds() {
    }

    /**
     * @param type type de clic Bukkit
     * @return la nature du clic ; tout ce qui n'est pas un clic gauche, droit ou avec
     *         majuscule (touche numerique, jet, double clic, main secondaire...) donne OTHER
     */
    static MenuClick of(ClickType type) {
        return switch (type) {
            case LEFT -> MenuClick.LEFT;
            case RIGHT -> MenuClick.RIGHT;
            case SHIFT_LEFT, SHIFT_RIGHT -> MenuClick.SHIFT;
            default -> MenuClick.OTHER;
        };
    }
}
