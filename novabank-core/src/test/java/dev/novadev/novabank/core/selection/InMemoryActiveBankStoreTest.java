package dev.novadev.novabank.core.selection;

import dev.novadev.novabank.core.testing.InMemoryActiveBankStore;

/**
 * Contrat {@link ActiveBankStore} applique a l'implementation en memoire.
 */
class InMemoryActiveBankStoreTest extends ActiveBankStoreContractTest {

    @Override
    protected ActiveBankStore newStore() {
        return new InMemoryActiveBankStore();
    }
}
