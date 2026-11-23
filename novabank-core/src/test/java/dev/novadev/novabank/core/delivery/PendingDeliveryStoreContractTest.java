package dev.novadev.novabank.core.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.novadev.novabank.api.OperationId;
import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.core.testing.ConcurrentRunner;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Contrat commun a toutes les implementations de {@link PendingDeliveryStore}.
 *
 * <p>Chaque backend etend cette classe et fournit une file vide via {@link #newStore()}.
 */
public abstract class PendingDeliveryStoreContractTest {

    private static final ItemKey STONE = ItemKey.minecraft("stone");
    private static final ItemKey CUSTOM = ItemKey.parse("itemsadder:pack/ruby_block");

    private PendingDeliveryStore store;

    /**
     * @return une file vide, propre a chaque test
     */
    protected abstract PendingDeliveryStore newStore();

    @BeforeEach
    void openStore() {
        store = newStore();
    }

    @Test
    void une_livraison_ajoutee_est_relue_a_l_identique() {
        PendingDelivery delivery = new PendingDelivery(OperationId.random(), UUID.randomUUID(), CUSTOM, Long.MAX_VALUE);

        store.add(delivery);

        assertThat(store.find(delivery.operationId())).contains(delivery);
        assertThat(store.pendingFor(delivery.playerId())).containsExactly(delivery);
    }

    @Test
    void une_livraison_inconnue_est_absente() {
        assertThat(store.find(OperationId.random())).isEmpty();
        assertThat(store.pendingFor(UUID.randomUUID())).isEmpty();
    }

    @Test
    void add_est_idempotent_par_retrait_et_conserve_la_premiere_livraison() {
        OperationId id = OperationId.random();
        UUID player = UUID.randomUUID();
        PendingDelivery first = new PendingDelivery(id, player, STONE, 3L);

        store.add(first);
        store.add(new PendingDelivery(id, player, STONE, 99L));

        assertThat(store.find(id)).contains(first);
        assertThat(store.pendingFor(player)).containsExactly(first);
    }

    @Test
    void pendingFor_ne_renvoie_que_les_livraisons_du_joueur() {
        UUID player = UUID.randomUUID();
        PendingDelivery mine1 = new PendingDelivery(OperationId.random(), player, STONE, 1L);
        PendingDelivery mine2 = new PendingDelivery(OperationId.random(), player, CUSTOM, 2L);
        PendingDelivery other = new PendingDelivery(OperationId.random(), UUID.randomUUID(), STONE, 5L);

        store.add(mine1);
        store.add(other);
        store.add(mine2);

        assertThat(store.pendingFor(player)).containsExactlyInAnyOrder(mine1, mine2);
    }

    @Test
    void les_ajouts_concurrents_d_une_meme_livraison_n_en_creent_qu_une() throws Exception {
        OperationId id = OperationId.random();
        UUID player = UUID.randomUUID();

        ConcurrentRunner.run(8, index -> store.add(new PendingDelivery(id, player, STONE, index + 1L)));

        assertThat(store.pendingFor(player)).hasSize(1);
        assertThat(store.find(id)).isPresent();
    }

    @Test
    void une_remise_partielle_reduit_la_quantite_due() {
        UUID player = UUID.randomUUID();
        PendingDelivery delivery = new PendingDelivery(OperationId.random(), player, STONE, 10L);
        store.add(delivery);

        assertThat(store.markDelivered(delivery.operationId(), 4L)).isTrue();

        PendingDelivery remaining = new PendingDelivery(delivery.operationId(), player, STONE, 6L);
        assertThat(store.find(delivery.operationId())).contains(remaining);
        assertThat(store.pendingFor(player)).containsExactly(remaining);
    }

    @Test
    void une_livraison_entierement_remise_disparait_et_ne_peut_pas_etre_reajoutee() {
        UUID player = UUID.randomUUID();
        PendingDelivery delivery = new PendingDelivery(OperationId.random(), player, STONE, 5L);
        store.add(delivery);

        assertThat(store.markDelivered(delivery.operationId(), 2L)).isTrue();
        assertThat(store.markDelivered(delivery.operationId(), 3L)).isTrue();
        store.add(delivery);

        assertThat(store.find(delivery.operationId())).isEmpty();
        assertThat(store.pendingFor(player)).isEmpty();
    }

    @Test
    void une_remise_superieure_au_reste_du_ou_inconnue_est_refusee_sans_effet() {
        PendingDelivery delivery = new PendingDelivery(OperationId.random(), UUID.randomUUID(), CUSTOM, 3L);
        store.add(delivery);

        assertThat(store.markDelivered(delivery.operationId(), 4L)).isFalse();
        assertThat(store.markDelivered(OperationId.random(), 1L)).isFalse();

        assertThat(store.find(delivery.operationId())).contains(delivery);
        assertThatThrownBy(() -> store.markDelivered(delivery.operationId(), 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void les_remises_concurrentes_ne_depassent_jamais_la_quantite_due() throws Exception {
        UUID player = UUID.randomUUID();
        PendingDelivery delivery = new PendingDelivery(OperationId.random(), player, STONE, 5L);
        store.add(delivery);
        AtomicInteger accepted = new AtomicInteger();

        ConcurrentRunner.run(8, index -> {
            if (store.markDelivered(delivery.operationId(), 1L)) {
                accepted.incrementAndGet();
            }
        });

        assertThat(accepted.get()).isEqualTo(5);
        assertThat(store.pendingFor(player)).isEmpty();
    }
}
