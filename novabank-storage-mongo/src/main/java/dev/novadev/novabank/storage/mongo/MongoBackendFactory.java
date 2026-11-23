package dev.novadev.novabank.storage.mongo;

import dev.novadev.novabank.core.storage.BackendParameters;
import dev.novadev.novabank.core.storage.StorageBackend;
import dev.novadev.novabank.core.storage.StorageBackendFactory;

/**
 * Point d'entree du module, instancie par son nom de classe par le chargeur de modules embarques.
 *
 * <p>Parametres attendus : voir {@link MongoConnectionSettings}.
 */
public final class MongoBackendFactory implements StorageBackendFactory {

    /**
     * Constructeur public sans argument, exige par le chargeur de modules.
     */
    public MongoBackendFactory() {
        // Aucun etat : chaque ouverture cree son propre client.
    }

    @Override
    public StorageBackend open(BackendParameters parameters) {
        return MongoBackend.open(MongoConnectionSettings.from(parameters), parameters.clock(), parameters.logger());
    }
}
