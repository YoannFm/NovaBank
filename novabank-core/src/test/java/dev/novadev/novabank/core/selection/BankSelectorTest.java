package dev.novadev.novabank.core.selection;

import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.api.entity.BankRole;
import dev.novadev.novabank.api.entity.EntityMembership;
import dev.novadev.novabank.api.entity.EntityRef;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BankSelectorTest {

    private static final EntityRef FIRST = new EntityRef("land", "a");
    private static final EntityRef SECOND = new EntityRef("land", "b");
    private static final EntityRef OTHER = new EntityRef("land", "z");
    private static final List<EntityMembership> MEMBERSHIPS = List.of(
            new EntityMembership(new EntityRef("player", "p"), BankRole.OWNER),
            new EntityMembership(FIRST, BankRole.OWNER), new EntityMembership(SECOND, BankRole.MEMBER));

    private static Optional<EntityRef> pick(Optional<EntityRef> preferred, Optional<EntityRef> chosen) {
        return BankSelector.selectOfType(MEMBERSHIPS, "land", preferred, chosen).map(EntityMembership::entity);
    }

    @Test
    void l_entite_preferee_par_l_hote_passe_avant_le_choix_du_joueur() {
        assertThat(pick(Optional.of(SECOND), Optional.of(FIRST))).contains(SECOND);
    }

    @Test
    void sans_preference_le_choix_precedent_est_garde() {
        assertThat(pick(Optional.empty(), Optional.of(SECOND))).contains(SECOND);
    }

    @Test
    void une_entite_dont_le_joueur_n_est_pas_membre_est_ignoree() {
        assertThat(pick(Optional.of(OTHER), Optional.of(OTHER))).contains(FIRST);
        assertThat(pick(Optional.empty(), Optional.of(new EntityRef("player", "p")))).contains(FIRST);
    }
}
