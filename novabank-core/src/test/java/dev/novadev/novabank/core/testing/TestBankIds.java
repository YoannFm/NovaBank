package dev.novadev.novabank.core.testing;

import dev.novadev.novabank.api.BankId;
import java.util.UUID;

/**
 * Identifiants de banque pour les tests. Le code de production n'en cree que par {@link BankId#forOwner} (ou les relit
 * en base) : un identifiant aleatoire n'appartiendrait a aucune entite, il n'existe donc que dans les tests.
 */
public final class TestBankIds {

    private TestBankIds() {
    }

    /** @return un identifiant de banque inedit, sans proprietaire */
    public static BankId random() {
        return new BankId(UUID.randomUUID());
    }
}
