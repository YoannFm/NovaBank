package dev.novadev.novabank.config.model;

import java.util.Objects;

/**
 * Section {@code storage.sqlite}.
 *
 * @param file chemin du fichier de base, relatif au dossier du plugin s'il n'est pas absolu
 */
public record SqliteSettings(String file) {

    /**
     * Verifie la presence du chemin.
     *
     * @throws NullPointerException si le chemin est nul
     */
    public SqliteSettings {
        Objects.requireNonNull(file, "file");
    }
}
