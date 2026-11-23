package dev.novadev.novabank.core.testing;

import dev.novadev.novabank.core.storage.StorageBackend;
import dev.novadev.novabank.core.storage.StorageContentContractTest;

/**
 * Contrat du contenu applique au backend en memoire (doublure des tests de migration).
 */
class InMemoryStorageContentTest extends StorageContentContractTest {

    @Override
    protected StorageBackend newBackend() {
        return new InMemoryStorageBackend();
    }
}
