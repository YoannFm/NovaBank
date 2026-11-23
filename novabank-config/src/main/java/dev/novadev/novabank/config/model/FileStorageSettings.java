package dev.novadev.novabank.config.model;

import java.util.Objects;

/**
 * Section {@code storage.file} : stockage YAML / JSON.
 *
 * @param directory dossier des fichiers de banques, relatif au dossier du plugin s'il n'est pas absolu
 */
public record FileStorageSettings(String directory) {

    /**
     * Verifie la presence du dossier.
     *
     * @throws NullPointerException si le dossier est nul
     */
    public FileStorageSettings {
        Objects.requireNonNull(directory, "directory");
    }
}
