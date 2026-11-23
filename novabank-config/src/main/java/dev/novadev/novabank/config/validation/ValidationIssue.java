package dev.novadev.novabank.config.validation;

import java.util.Objects;

/**
 * Probleme detecte dans la configuration.
 *
 * <p>Le message ne contient jamais la valeur d'un chemin secret.
 *
 * @param severity gravite
 * @param path     chemin de la cle concernee, par exemple {@code storage.sql.port}
 *                 ou {@code items[2].price}
 * @param message  explication en francais, destinee a l'administrateur
 */
public record ValidationIssue(Severity severity, String path, String message) {

    /**
     * Valide la presence des trois champs.
     *
     * @throws NullPointerException si un champ est nul
     */
    public ValidationIssue {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(message, "message");
    }

    /**
     * Forme lisible {@code [GRAVITE] chemin : message}.
     *
     * @return l'issue au format texte
     */
    @Override
    public String toString() {
        return "[" + severity.label() + "] " + path + " : " + message;
    }
}
