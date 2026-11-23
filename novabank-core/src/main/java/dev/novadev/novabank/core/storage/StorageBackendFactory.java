package dev.novadev.novabank.core.storage;

import dev.novadev.novabank.core.repository.StorageException;

/**
 * Fabrique d'un backend de stockage. Une implementation publique avec un constructeur public
 * sans argument est instanciee par son nom de classe (module embarque, decision D18).
 */
public interface StorageBackendFactory {

    /**
     * Ouvre le backend : connexion, verification ou migration du schema.
     *
     * @param parameters reglages du backend (cles propres a chaque backend), horloge et journal
     * @return le backend pret, a fermer par l'appelant
     * @throws IllegalArgumentException si un parametre est absent ou invalide (le message ne
     *                                  contient jamais la valeur d'un parametre secret)
     * @throws StorageException         si la base est injoignable, refuse la connexion ou porte
     *                                  un schema plus recent que celui du backend
     */
    StorageBackend open(BackendParameters parameters);
}
