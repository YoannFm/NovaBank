package dev.novadev.novabank.core.access;

import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.api.entity.BankRole;
import dev.novadev.novabank.api.operation.OperationType;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RoleRightsTest {

    @Test
    void droits_par_defaut_tout_membre_depose_seuls_proprietaire_et_gestionnaires_retirent() {
        RoleRights rights = RoleRights.DEFAULT;

        assertThat(rights.allows(OperationType.DEPOSIT, BankRole.MEMBER)).isTrue();
        assertThat(rights.allows(OperationType.WITHDRAW, BankRole.MEMBER)).isFalse();
        assertThat(rights.allows(OperationType.WITHDRAW, BankRole.ADMIN)).isTrue();
        assertThat(rights.allows(OperationType.WITHDRAW, BankRole.OWNER)).isTrue();
    }

    @Test
    void un_reset_n_est_jamais_autorise_par_role() {
        RoleRights everything = new RoleRights(Set.of(BankRole.values()), Set.of(BankRole.values()));

        assertThat(everything.allows(OperationType.RESET, BankRole.OWNER)).isFalse();
        assertThat(new RoleRights(Set.of(), Set.of()).allows(OperationType.DEPOSIT, BankRole.OWNER)).isFalse();
    }
}
