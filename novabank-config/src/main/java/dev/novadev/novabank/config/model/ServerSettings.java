package dev.novadev.novabank.config.model;

import java.util.Objects;

/**
 * Section {@code server} : identite de ce serveur dans un reseau.
 *
 * @param id    identifiant unique du serveur ({@code [a-z0-9_-]+})
 * @param group groupe de serveurs partageant les memes banques
 */
public record ServerSettings(String id, String group) {

    /**
     * Verifie la presence des champs.
     *
     * @throws NullPointerException si un champ est nul
     */
    public ServerSettings {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(group, "group");
    }
}
