package dev.novadev.novabank.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.novadev.novabank.core.testing.TestBankIds;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdentifiersTest {

    @Test
    void les_identifiants_aleatoires_sont_distincts_et_affichent_leur_uuid() {
        UUID uuid = UUID.randomUUID();

        assertThat(OperationId.random()).isNotEqualTo(OperationId.random());
        assertThat(TestBankIds.random()).isNotEqualTo(TestBankIds.random());
        assertThat(new BankId(uuid).toString()).isEqualTo(uuid.toString());
        assertThat(new OperationId(uuid).toString()).isEqualTo(uuid.toString());
    }

    @Test
    void un_identifiant_nul_est_refuse() {
        assertThatThrownBy(() -> new BankId(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new OperationId(null)).isInstanceOf(NullPointerException.class);
    }
}
