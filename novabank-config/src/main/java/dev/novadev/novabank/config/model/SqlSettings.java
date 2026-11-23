package dev.novadev.novabank.config.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Section {@code storage.sql} : connexion MySQL / MariaDB.
 *
 * @param host     hote du serveur SQL
 * @param port     port TCP
 * @param database nom de la base
 * @param username utilisateur (secret)
 * @param password mot de passe (secret)
 * @param pool     reglages du pool de connexions
 * @param options  options JDBC supplementaires, dans l'ordre du fichier
 */
public record SqlSettings(String host, int port, String database, Secret username, Secret password,
                          PoolSettings pool, Map<String, String> options) {

    /**
     * Verifie la presence des champs et fige les options.
     *
     * @throws NullPointerException si un champ est nul
     */
    public SqlSettings {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(database, "database");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(password, "password");
        Objects.requireNonNull(pool, "pool");
        options = Collections.unmodifiableMap(new LinkedHashMap<>(options));
    }
}
