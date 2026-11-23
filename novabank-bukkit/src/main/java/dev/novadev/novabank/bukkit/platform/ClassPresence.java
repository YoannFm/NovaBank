package dev.novadev.novabank.bukkit.platform;

import java.util.Objects;

/**
 * Indique si une classe est visible, sans l'initialiser. Permet de tester la detection
 * de plateforme sans serveur.
 */
@FunctionalInterface
public interface ClassPresence {

    /**
     * @param className nom complet de la classe
     * @return {@code true} si la classe peut etre chargee
     */
    boolean isPresent(String className);

    /**
     * Presence vue depuis un chargeur de classes (celui du serveur en production).
     *
     * @param loader chargeur interroge
     * @return la sonde
     */
    static ClassPresence of(ClassLoader loader) {
        Objects.requireNonNull(loader, "loader");
        return className -> {
            try {
                Class.forName(className, false, loader);
                return true;
            } catch (ClassNotFoundException | LinkageError absent) {
                return false;
            }
        };
    }
}
