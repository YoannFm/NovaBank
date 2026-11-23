package dev.novadev.novabank.bukkit.storage;

import java.util.Objects;

/**
 * Pilote du backend ouvert, pour le diagnostic.
 *
 * @param id       identifiant du pilote ({@code sqlite}, {@code mariadb}, {@code mongodb})
 * @param artifact version de l'artefact epingle
 * @param detail   precision sur le chargement (version JDBC annoncee, module embarque)
 */
public record StorageDriver(String id, String artifact, String detail) {

    /**
     * @throws NullPointerException si un champ est nul
     */
    public StorageDriver {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(artifact, "artifact");
        Objects.requireNonNull(detail, "detail");
    }
}
