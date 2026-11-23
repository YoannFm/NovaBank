package dev.novadev.novabank.core.delivery;

import dev.novadev.novabank.core.testing.InMemoryPendingDeliveryStore;

/**
 * Contrat {@link PendingDeliveryStore} applique a l'implementation en memoire.
 */
class InMemoryPendingDeliveryStoreTest extends PendingDeliveryStoreContractTest {

    @Override
    protected PendingDeliveryStore newStore() {
        return new InMemoryPendingDeliveryStore();
    }
}
