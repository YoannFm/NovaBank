package dev.novadev.novabank.bukkit.storage;

import dev.novadev.novabank.config.model.StorageType;
import dev.novadev.novabank.core.storage.StorageBackend;
import java.util.Objects;

/**
 * Stockage ouvert au demarrage. Sa fermeture est geree par le cycle de vie, pas par
 * cet objet.
 *
 * @param type     backend
 * @param backend  backend ouvert (ports du coeur)
 * @param driver   pilote charge, pour le diagnostic
 * @param location emplacement de la base, pour les journaux : fichier SQLite, {@code hote:port/base}
 *                 ou {@code hotes/base} (jamais d'identifiants)
 */
public record ActiveStorage(StorageType type, StorageBackend backend, StorageDriver driver, String location) {

    /**
     * @throws NullPointerException si un champ est nul
     */
    public ActiveStorage {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(backend, "backend");
        Objects.requireNonNull(driver, "driver");
        Objects.requireNonNull(location, "location");
    }
}
