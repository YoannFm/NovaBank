package dev.novadev.novabank.config.model;

import java.util.Objects;

/**
 * Section {@code storage.drivers} : pilotes BDD telecharges au demarrage (D6).
 *
 * <p>La verification SHA-256 des pilotes est toujours active ; elle n'est pas
 * representee ici car elle n'est pas configurable.
 *
 * @param directory dossier des pilotes, relatif au dossier du plugin s'il n'est pas absolu
 */
public record DriverSettings(String directory) {

    /**
     * Verifie la presence du dossier.
     *
     * @throws NullPointerException si le dossier est nul
     */
    public DriverSettings {
        Objects.requireNonNull(directory, "directory");
    }
}
