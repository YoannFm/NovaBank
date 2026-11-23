package dev.novadev.novabank.bukkit.integration;

import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.api.entity.BankRole;
import org.junit.jupiter.api.Test;

class IridiumSkyblockOwnershipTest {

    private static final int OWNER = -2;

    @Test
    void proprietaire_co_proprietaire_et_membres() {
        // Rangs par defaut : 1 membre, 2 moderateur, 3 co-proprietaire ; proprietaire = -2.
        assertThat(IridiumSkyblockOwnership.role(OWNER, OWNER, 3)).isEqualTo(BankRole.OWNER);
        assertThat(IridiumSkyblockOwnership.role(3, OWNER, 3)).isEqualTo(BankRole.ADMIN);
        assertThat(IridiumSkyblockOwnership.role(2, OWNER, 3)).isEqualTo(BankRole.MEMBER);
        // Avant le correctif, tout rang >= -2 etait proprietaire : un simple membre pouvait retirer.
        assertThat(IridiumSkyblockOwnership.role(1, OWNER, 3)).isEqualTo(BankRole.MEMBER);
    }

    @Test
    void table_des_rangs_illisible_personne_n_est_admin() {
        assertThat(IridiumSkyblockOwnership.role(3, OWNER, Integer.MAX_VALUE)).isEqualTo(BankRole.MEMBER);
    }
}
