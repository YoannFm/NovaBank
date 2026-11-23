package dev.novadev.novabank.core.application;

import static dev.novadev.novabank.core.application.ApplicationFixture.PERSONAL;
import static dev.novadev.novabank.core.application.ApplicationFixture.PLAYER;
import static dev.novadev.novabank.core.application.ApplicationFixture.STONE;
import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.core.access.Permissions;
import dev.novadev.novabank.core.delivery.PendingDelivery;
import dev.novadev.novabank.core.delivery.UndeliveredPolicy;
import dev.novadev.novabank.core.journal.OperationState;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class WithdrawalFlowTest {

    private ApplicationFixture fixture = new ApplicationFixture();

    private void bankHolds(long stones) {
        fixture.inventory.put(PLAYER, STONE, stones);
        fixture.depositOne(STONE, stones);
    }

    @Test
    void un_retrait_reserve_remet_confirme_puis_sauvegarde_le_joueur() {
        bankHolds(50);

        TransferResult result = fixture.withdraw(STONE, Amount.of(20));

        assertThat(result.status()).isEqualTo(TransferStatus.SUCCESS);
        assertThat(result.quantity()).isEqualTo(20);
        assertThat(result.undelivered()).isZero();
        assertThat(result.valueDelta()).isEqualByComparingTo("-20");
        assertThat(result.bankValue()).hasValueSatisfying(value -> assertThat(value).isEqualByComparingTo("30"));
        assertThat(fixture.inventory.quantity(PLAYER, STONE)).isEqualTo(20);
        assertThat(fixture.stock(PERSONAL, STONE)).isEqualTo(30);
        assertThat(fixture.repository.direct().reservedWithdrawals()).isEmpty();
        int lastSave = fixture.log.events().lastIndexOf("sauvegarde:" + PLAYER);
        assertThat(fixture.log.events().subList(0, lastSave))
                .anySatisfy(event -> assertThat(event).startsWith("base:settle"));
        int give = fixture.log.events().lastIndexOf("inventaire:don:" + STONE + ":20");
        assertThat(give).isLessThan(lastSave);
    }

    @Test
    void retrait_partiel_inventaire_plein_le_reste_passe_en_livraison_differee() {
        bankHolds(50);
        fixture.inventory.capacity(PLAYER, 15);

        TransferResult result = fixture.withdraw(STONE, Amount.of(40));

        assertThat(result.quantity()).isEqualTo(15);
        assertThat(result.undelivered()).isEqualTo(25);
        assertThat(fixture.stock(PERSONAL, STONE)).isEqualTo(10);
        assertThat(fixture.deliveries.pendingFor(PLAYER)).extracting(PendingDelivery::quantity).containsExactly(25L);
    }

    @Test
    void retrait_partiel_avec_retour_en_banque_le_reste_est_re_credite() {
        fixture = new ApplicationFixture(UndeliveredPolicy.RETURN_TO_BANK);
        bankHolds(50);
        fixture.inventory.capacity(PLAYER, 15);

        TransferResult result = fixture.withdraw(STONE, Amount.of(40));

        assertThat(result.quantity()).isEqualTo(15);
        assertThat(result.undelivered()).isEqualTo(25);
        assertThat(result.valueDelta()).isEqualByComparingTo("-15");
        assertThat(fixture.stock(PERSONAL, STONE)).isEqualTo(35);
        assertThat(fixture.deliveries.size()).isZero();
    }

    @Test
    void joueur_parti_avant_la_remise_le_retrait_est_confirme_avec_zero_remis() {
        bankHolds(50);
        fixture.service.withdraw(PLAYER, STONE, Amount.of(10), result -> { });
        fixture.scheduler.runOne();
        fixture.scheduler.runOne();
        fixture.scheduler.disconnect(PLAYER);
        fixture.scheduler.runAll();

        assertThat(fixture.stock(PERSONAL, STONE)).isEqualTo(40);
        assertThat(fixture.deliveries.pendingFor(PLAYER)).extracting(PendingDelivery::quantity).containsExactly(10L);
        assertThat(fixture.repository.direct().reservedWithdrawals()).isEmpty();
    }

    @Test
    void tout_retirer_est_borne_par_le_stock_et_la_limite() {
        bankHolds(30);

        assertThat(fixture.withdraw(STONE, Amount.everything()).quantity()).isEqualTo(30);
        assertThat(fixture.withdraw(STONE, Amount.everything()).status()).isEqualTo(TransferStatus.INSUFFICIENT_STOCK);
    }

    @Test
    void stock_insuffisant_ou_limite_depassee_refuses_sans_rien_remettre() {
        bankHolds(30);

        assertThat(fixture.withdraw(STONE, Amount.of(31)).status()).isEqualTo(TransferStatus.INSUFFICIENT_STOCK);
        assertThat(fixture.withdraw(STONE, Amount.of(101)).status()).isEqualTo(TransferStatus.QUANTITY_LIMIT);
        assertThat(fixture.inventory.quantity(PLAYER, STONE)).isZero();
        assertThat(fixture.stock(PERSONAL, STONE)).isEqualTo(30);
    }

    @Test
    void confirmation_impossible_le_retrait_reste_reserve_et_le_joueur_n_est_pas_sauvegarde() {
        bankHolds(30);
        int savesBefore = (int) fixture.log.events().stream().filter(event -> event.startsWith("sauvegarde")).count();
        List<TransferResult> received = new ArrayList<>();
        fixture.service.withdraw(PLAYER, STONE, Amount.of(10), received::add);
        fixture.scheduler.runOne();
        fixture.scheduler.runOne();
        fixture.scheduler.runOne();
        fixture.repository.failNextWrites(10);
        fixture.scheduler.runAll();

        assertThat(fixture.repository.direct().reservedWithdrawals()).singleElement()
                .satisfies(record -> assertThat(record.state()).isEqualTo(OperationState.RESERVED));
        assertThat(fixture.inventory.quantity(PLAYER, STONE)).isEqualTo(10);
        assertThat(fixture.log.events().stream().filter(event -> event.startsWith("sauvegarde"))).hasSize(savesBefore);
        assertThat(received).singleElement().satisfies(result -> {
            assertThat(result.status()).isEqualTo(TransferStatus.CONFIRMATION_PENDING);
            assertThat(result.quantity()).isEqualTo(10);
        });
    }

    @Test
    void sans_permission_de_retrait_rien_n_est_reserve() {
        bankHolds(30);
        fixture.deniedPermissions.add(Permissions.WITHDRAW);

        assertThat(fixture.withdraw(STONE, Amount.of(1)).status()).isEqualTo(TransferStatus.NO_PERMISSION);
        assertThat(fixture.stock(PERSONAL, STONE)).isEqualTo(30);
    }

    @Test
    void base_indisponible_a_la_reservation_rien_n_est_remis() {
        bankHolds(30);
        fixture.repository.failNextWrites(10);

        TransferResult result = fixture.withdraw(STONE, Amount.of(5));

        assertThat(result.status()).isEqualTo(TransferStatus.STORAGE_ERROR);
        assertThat(fixture.inventory.quantity(PLAYER, STONE)).isZero();
        assertThat(fixture.stock(PERSONAL, STONE)).isEqualTo(30);
    }
}
