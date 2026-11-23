package dev.novadev.novabank.config.model;

import java.util.Objects;

/**
 * Section {@code storage}. Toutes les sous-sections sont lues et validees, meme
 * celles du backend inactif, pour permettre une migration entre backends.
 *
 * @param type                backend actif
 * @param sql                 connexion MySQL / MariaDB
 * @param sqlite              fichier SQLite
 * @param file                dossier YAML / JSON
 * @param mongodb             connexion MongoDB
 * @param saveIntervalSeconds intervalle de sauvegarde des backends fichier, en secondes
 * @param drivers             pilotes BDD
 */
public record StorageSettings(StorageType type, SqlSettings sql, SqliteSettings sqlite, FileStorageSettings file,
                              MongoSettings mongodb, int saveIntervalSeconds, DriverSettings drivers) {

    /**
     * Verifie la presence des champs.
     *
     * @throws NullPointerException si un champ est nul
     */
    public StorageSettings {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(sql, "sql");
        Objects.requireNonNull(sqlite, "sqlite");
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(mongodb, "mongodb");
        Objects.requireNonNull(drivers, "drivers");
    }
}
