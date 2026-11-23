package dev.novadev.novabank.core.repository;

import dev.novadev.novabank.core.testing.InMemoryBankRepository;

/**
 * Contrat {@link BankRepository} applique a l'implementation en memoire.
 */
class InMemoryBankRepositoryTest extends BankRepositoryContractTest {

    @Override
    protected BankRepository newRepository() {
        return new InMemoryBankRepository();
    }
}
