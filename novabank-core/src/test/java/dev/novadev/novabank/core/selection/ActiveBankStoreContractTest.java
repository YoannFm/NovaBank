package dev.novadev.novabank.core.selection;

import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.api.entity.EntityRef;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Contrat commun a toutes les implementations de {@link ActiveBankStore}.
 */
public abstract class ActiveBankStoreContractTest {

    private ActiveBankStore store;

    /**
     * @return un stockage vide, propre a chaque test
     */
    protected abstract ActiveBankStore newStore();

    @BeforeEach
    void openStore() {
        store = newStore();
    }

    @Test
    void un_joueur_sans_choix_n_a_pas_de_banque_active_enregistree() {
        assertThat(store.find(UUID.randomUUID())).isEmpty();
    }

    @Test
    void un_choix_enregistre_est_relu_a_l_identique() {
        UUID player = UUID.randomUUID();
        EntityRef island = new EntityRef("island", "Ile de Yo:42");

        store.save(player, island);

        assertThat(store.find(player)).contains(island);
    }

    @Test
    void un_nouveau_choix_remplace_le_precedent_sans_toucher_aux_autres_joueurs() {
        UUID player = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        store.save(player, EntityRef.player(player));
        store.save(other, EntityRef.player(other));

        store.save(player, new EntityRef("faction", "rouge"));

        assertThat(store.find(player)).contains(new EntityRef("faction", "rouge"));
        assertThat(store.find(other)).contains(EntityRef.player(other));
    }
}
