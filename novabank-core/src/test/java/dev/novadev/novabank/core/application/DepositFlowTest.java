package dev.novadev.novabank.core.application;

import static dev.novadev.novabank.core.application.ApplicationFixture.DIAMOND;
import static dev.novadev.novabank.core.application.ApplicationFixture.DIRT;
import static dev.novadev.novabank.core.application.ApplicationFixture.PERSONAL;
import static dev.novadev.novabank.core.application.ApplicationFixture.PLAYER;
import static dev.novadev.novabank.core.application.ApplicationFixture.STONE;
import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.api.operation.OperationStatus;
import dev.novadev.novabank.core.access.Permissions;
import dev.novadev.novabank.core.delivery.PendingDelivery;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class DepositFlowTest {

    private final ApplicationFixture fixture = new ApplicationFixture();

    @Test
    void un_depot_retire_les_items_valide_en_base_puis_sauvegarde_le_joueur() {
        fixture.inventory.put(PLAYER, STONE, 64);

        TransferResult result = fixture.depositOne(STONE, 40);

        assertThat(result.status()).isEqualTo(TransferStatus.SUCCESS);
        assertThat(result.quantity()).isEqualTo(40);
        assertThat(result.valueDelta()).isEqualByComparingTo("40");
        assertThat(result.bankValue()).hasValueSatisfying(value -> assertThat(value).isEqualByComparingTo("40"));
        assertThat(result.bank()).contains(PERSONAL);
        assertThat(fixture.inventory.quantity(PLAYER, STONE)).isEqualTo(24);
        assertThat(fixture.stock(PERSONAL, STONE)).isEqualTo(40);
        int removal = fixture.log.indexOf("inventaire:retrait");
        int commit = fixture.log.indexOf("base:apply");
        int save = fixture.log.indexOf("sauvegarde");
        assertThat(removal).isNotNegative().isLessThan(commit);
        assertThat(commit).isLessThan(save);
    }

    @Test
    void la_banque_personnelle_est_creee_a_la_premiere_operation_avec_un_identifiant_deterministe() {
        fixture.inventory.put(PLAYER, STONE, 5);

        fixture.depositOne(STONE, 5);

        assertThat(fixture.bank(PERSONAL).owner()).isEqualTo(PERSONAL);
    }

    @Test
    void un_refus_de_la_banque_rend_les_items_sans_sauvegarde() {
        fixture.catalog.remove(STONE);
        fixture.inventory.put(PLAYER, STONE, 10);

        TransferResult result = fixture.depositOne(STONE, 10);

        assertThat(result.status()).isEqualTo(TransferStatus.NOT_ACCEPTED);
        assertThat(result.ledgerStatus()).contains(OperationStatus.NOT_ACCEPTED);
        assertThat(fixture.inventory.quantity(PLAYER, STONE)).isEqualTo(10);
        assertThat(fixture.log.indexOf("sauvegarde")).isNegative();
        assertThat(fixture.deliveries.size()).isZero();
    }

    @Test
    void une_panne_de_stockage_rend_les_items() {
        fixture.inventory.put(PLAYER, STONE, 10);
        fixture.repository.failNextWrites(10);

        TransferResult result = fixture.depositOne(STONE, 10);

        assertThat(result.status()).isEqualTo(TransferStatus.STORAGE_ERROR);
        assertThat(fixture.inventory.quantity(PLAYER, STONE)).isEqualTo(10);
        assertThat(fixture.log.indexOf("base:apply")).isNegative();
        assertThat(fixture.log.indexOf("sauvegarde")).isNegative();
    }

    @Test
    void une_panne_passagere_est_rattrapee_par_une_nouvelle_tentative() {
        fixture.inventory.put(PLAYER, STONE, 10);
        fixture.repository.failNextWrites(1);

        TransferResult result = fixture.depositOne(STONE, 10);

        assertThat(result.status()).isEqualTo(TransferStatus.SUCCESS);
        assertThat(fixture.stock(PERSONAL, STONE)).isEqualTo(10);
        assertThat(fixture.inventory.quantity(PLAYER, STONE)).isZero();
    }

    @Test
    void une_reponse_perdue_apres_validation_n_entraine_ni_double_depot_ni_restitution() {
        fixture.inventory.put(PLAYER, STONE, 10);
        fixture.repository.failAfterCommit(1);

        TransferResult result = fixture.depositOne(STONE, 10);

        assertThat(result.status()).isEqualTo(TransferStatus.SUCCESS);
        assertThat(fixture.stock(PERSONAL, STONE)).isEqualTo(10);
        assertThat(fixture.inventory.quantity(PLAYER, STONE)).isZero();
        assertThat(fixture.log.indexOf("sauvegarde")).isPositive();
    }

    @Test
    void base_illisible_apres_toutes_les_tentatives_les_items_ne_sont_ni_rendus_ni_comptes() {
        fixture.inventory.put(PLAYER, STONE, 10);
        fixture.depositOne(STONE, 1);
        fixture.service.deposit(PLAYER, ItemChoice.of(STONE), Amount.of(4), results -> { });
        fixture.scheduler.runOne();
        fixture.scheduler.runOne();
        fixture.scheduler.runOne();
        fixture.repository.failEverything(true);
        fixture.scheduler.runAll();

        assertThat(fixture.inventory.quantity(PLAYER, STONE)).isEqualTo(5);
        assertThat(fixture.deliveries.size()).isZero();
    }

    @Test
    void joueur_parti_avant_la_restitution_les_items_passent_en_livraison_differee() {
        fixture.inventory.put(PLAYER, STONE, 10);
        fixture.repository.failNextWrites(10);
        fixture.service.deposit(PLAYER, ItemChoice.of(STONE), Amount.of(10), results -> { });
        fixture.scheduler.runOne();
        fixture.scheduler.runOne();
        fixture.scheduler.runOne();
        fixture.scheduler.disconnect(PLAYER);
        fixture.scheduler.runAll();

        assertThat(fixture.deliveries.pendingFor(PLAYER)).singleElement()
                .satisfies(delivery -> assertThat(delivery.quantity()).isEqualTo(10));
    }

    @Test
    void inventaire_plein_a_la_restitution_le_surplus_passe_en_livraison_differee() {
        fixture.inventory.put(PLAYER, STONE, 10);
        fixture.repository.failNextWrites(10);
        fixture.inventory.capacity(PLAYER, 3);

        TransferResult result = fixture.depositOne(STONE, 10);

        assertThat(result.undelivered()).isEqualTo(7);
        assertThat(fixture.inventory.quantity(PLAYER, STONE)).isEqualTo(3);
        assertThat(fixture.deliveries.pendingFor(PLAYER)).extracting(PendingDelivery::quantity).containsExactly(7L);
    }

    @Test
    void tout_deposer_fait_une_operation_par_item_accepte() {
        fixture.inventory.put(PLAYER, STONE, 30).put(PLAYER, DIAMOND, 2).put(PLAYER, DIRT, 50);

        List<TransferResult> results = fixture.deposit(ItemChoice.allAccepted(), Amount.everything());

        assertThat(results).extracting(TransferResult::status).containsOnly(TransferStatus.SUCCESS);
        assertThat(results).extracting(TransferResult::quantity).containsExactly(30L, 2L);
        assertThat(fixture.inventory.quantity(PLAYER, DIRT)).isEqualTo(50);
        assertThat(fixture.bank(PERSONAL).totalQuantity()).isEqualTo(32);
        assertThat(fixture.log.events().stream().filter(event -> event.startsWith("sauvegarde"))).hasSize(1);
    }

    @Test
    void tout_deposer_est_borne_par_la_limite_par_operation() {
        fixture.inventory.put(PLAYER, STONE, 250);

        List<TransferResult> results = fixture.deposit(ItemChoice.of(STONE), Amount.everything());

        assertThat(results).singleElement().satisfies(result -> assertThat(result.quantity()).isEqualTo(100));
        assertThat(fixture.inventory.quantity(PLAYER, STONE)).isEqualTo(150);
    }

    @Test
    void une_quantite_superieure_a_la_limite_est_refusee_sans_toucher_a_l_inventaire() {
        fixture.inventory.put(PLAYER, STONE, 250);

        assertThat(fixture.depositOne(STONE, 101).status()).isEqualTo(TransferStatus.QUANTITY_LIMIT);
        assertThat(fixture.inventory.quantity(PLAYER, STONE)).isEqualTo(250);
    }

    @Test
    void items_insuffisants_ou_non_acceptes_sont_refuses() {
        fixture.inventory.put(PLAYER, STONE, 3).put(PLAYER, DIRT, 3);

        assertThat(fixture.depositOne(STONE, 4).status()).isEqualTo(TransferStatus.NOT_ENOUGH_ITEMS);
        assertThat(fixture.depositOne(DIRT, 1).status()).isEqualTo(TransferStatus.NOT_ACCEPTED);
        assertThat(fixture.inventory.quantity(PLAYER, STONE)).isEqualTo(3);
        assertThat(fixture.deposit(ItemChoice.held(), Amount.everything())).extracting(TransferResult::status)
                .containsExactly(TransferStatus.NOTHING_IN_HAND);
    }

    @Test
    void deposer_l_item_en_main() {
        fixture.inventory.put(PLAYER, DIAMOND, 5);
        fixture.inventory.hold(PLAYER, DIAMOND);

        List<TransferResult> results = fixture.deposit(ItemChoice.held(), Amount.of(2));

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.item()).contains(DIAMOND);
            assertThat(result.valueDelta()).isEqualByComparingTo(new BigDecimal("20"));
        });
    }

    @Test
    void sans_permission_rien_n_est_retire() {
        fixture.inventory.put(PLAYER, STONE, 3);
        fixture.deniedPermissions.add(Permissions.DEPOSIT);

        assertThat(fixture.depositOne(STONE, 3).status()).isEqualTo(TransferStatus.NO_PERMISSION);
        assertThat(fixture.inventory.quantity(PLAYER, STONE)).isEqualTo(3);
    }

    @Test
    void sans_sauvegarde_configuree_le_joueur_n_est_pas_sauvegarde() {
        fixture.withoutPlayerDataSave();
        fixture.inventory.put(PLAYER, STONE, 3);

        assertThat(fixture.depositOne(STONE, 3).status()).isEqualTo(TransferStatus.SUCCESS);
        assertThat(fixture.log.indexOf("sauvegarde")).isNegative();
    }

    @Test
    void executeur_io_sature_les_items_deja_retires_sont_rendus() {
        fixture.inventory.put(PLAYER, STONE, 8);
        fixture.service.deposit(PLAYER, ItemChoice.of(STONE), Amount.of(8), results -> { });
        fixture.scheduler.runOne();
        fixture.scheduler.runOne();
        fixture.scheduler.rejectIo(true);
        fixture.scheduler.runAll();

        assertThat(fixture.inventory.quantity(PLAYER, STONE)).isEqualTo(8);
    }
}
