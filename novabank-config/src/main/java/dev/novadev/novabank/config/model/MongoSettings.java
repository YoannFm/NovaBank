package dev.novadev.novabank.config.model;

import java.util.Objects;

/**
 * Section {@code storage.mongodb}.
 *
 * @param uri                      URI de connexion (secret : elle contient en general les identifiants)
 * @param database                 nom de la base
 * @param connectTimeoutMs         delai d'etablissement d'une connexion, en millisecondes
 * @param serverSelectionTimeoutMs delai maximal pour trouver un serveur utilisable, en millisecondes
 * @param socketTimeoutMs          delai maximal d'attente d'une reponse, en millisecondes
 */
public record MongoSettings(Secret uri, String database, int connectTimeoutMs, int serverSelectionTimeoutMs,
                            int socketTimeoutMs) {

    /**
     * Verifie la presence des champs.
     *
     * @throws NullPointerException si un champ est nul
     */
    public MongoSettings {
        Objects.requireNonNull(uri, "uri");
        Objects.requireNonNull(database, "database");
    }
}
