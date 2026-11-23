package dev.novadev.novabank.core.application;

import static dev.novadev.novabank.core.application.ApplicationFixture.PERSONAL;
import static dev.novadev.novabank.core.application.ApplicationFixture.PLAYER;
import static dev.novadev.novabank.core.application.ApplicationFixture.START;
import static dev.novadev.novabank.core.application.ApplicationFixture.STONE;
import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.api.OperationId;
import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.core.access.Permissions;
import dev.novadev.novabank.core.delivery.PendingDelivery;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Cas limites des flux : joueur parti, base indisponible, permissions de consultation.
 */
class FlowEdgeCasesTest {

    private final ApplicationFixture fixture = new ApplicationFixture();

    @Test
    void joueur_parti_avant_la_remise_des_livraisons_rien_n_est_marque() {
        fixture.deliveries.add(new PendingDelivery(OperationId.random(), PLAYER, STONE, 4));
        List<DeliveryReport> reports = new ArrayList<>();
        fixture.service.deliverPending(PLAYER, reports::add);
        fixture.scheduler.runOne();
        fixture.scheduler.disconnect(PLAYER);
        fixture.scheduler.runAll();

        assertThat(fixture.deliveries.pendingFor(PLAYER)).extracting(PendingDelivery::quantity).containsExactly(4L);
        fixture.scheduler.connect(PLAYER);
        fixture.service.deliverPending(PLAYER, reports::add);
        fixture.scheduler.runAll();
        assertThat(reports).last().isEqualTo(new DeliveryReport(4, 0));
    }

    @Test
    void inventaire_plein_aucune_remise_aucun_marquage() {
        fixture.deliveries.add(new PendingDelivery(OperationId.random(), PLAYER, STONE, 4));
        fixture.inventory.capacity(PLAYER, 0);
        List<DeliveryReport> reports = new ArrayList<>();

        fixture.service.deliverPending(PLAYER, reports::add);
        fixture.scheduler.runAll();

        assertThat(reports).containsExactly(new DeliveryReport(0, 4));
        assertThat(fixture.log.indexOf("sauvegarde")).isNegative();
    }

    @Test
    void livraisons_executeur_io_sature_rien_n_est_remis() {
        fixture.deliveries.add(new PendingDelivery(OperationId.random(), PLAYER, STONE, 4));
        fixture.scheduler.rejectIo(true);
        List<DeliveryReport> reports = new ArrayList<>();

        fixture.service.deliverPending(PLAYER, reports::add);
        fixture.scheduler.runAll();

        assertThat(reports).containsExactly(DeliveryReport.NOTHING);
        assertThat(fixture.inventory.quantity(PLAYER, STONE)).isZero();
    }

    @Test
    void consultation_et_choix_sans_permission_ou_base_indisponible() {
        fixture.deniedPermissions.add(Permissions.USE);
        List<BankView> views = new ArrayList<>();
        List<SelectionResult> selections = new ArrayList<>();

        fixture.service.info(PLAYER, views::add);
        fixture.service.select(PLAYER, "player", selections::add);
        fixture.scheduler.runAll();
        fixture.deniedPermissions.clear();
        fixture.scheduler.rejectIo(true);
        fixture.service.select(PLAYER, "player", selections::add);
        fixture.scheduler.runAll();

        assertThat(views).extracting(BankView::status).containsExactly(QueryStatus.NO_PERMISSION);
        assertThat(selections).extracting(SelectionResult::status)
                .containsExactly(QueryStatus.NO_PERMISSION, QueryStatus.STORAGE_ERROR);
    }

    @Test
    void consultation_sans_aucune_banque() {
        fixture.ownership.unregister(EntityRef.PLAYER);
        List<BankView> views = new ArrayList<>();

        fixture.service.info(PLAYER, views::add);
        fixture.scheduler.runAll();

        assertThat(views).extracting(BankView::status).containsExactly(QueryStatus.NO_BANK);
    }

    @Test
    void reconciliation_en_echec_comptee_et_retentee_au_demarrage_suivant() {
        fixture.inventory.put(PLAYER, STONE, 5);
        fixture.depositOne(STONE, 5);
        fixture.ledger.reserveWithdrawal(OperationId.random(), BankId.forOwner(PERSONAL), STONE, 2, PLAYER);
        fixture.repository.failNextWrites(1);

        ReconciliationReport report = new WithdrawalReconciler(fixture.ledger, fixture.repository)
                .reconcile(START.plusSeconds(1));

        assertThat(report).isEqualTo(new ReconciliationReport(0, 1, 0));
        assertThat(fixture.repository.direct().reservedWithdrawals()).hasSize(1);
    }

    @Test
    void retrait_joueur_parti_avant_le_debut() {
        fixture.scheduler.disconnect(PLAYER);
        List<TransferResult> results = new ArrayList<>();

        fixture.service.withdraw(PLAYER, STONE, Amount.of(1), results::add);
        fixture.scheduler.runAll();
        fixture.scheduler.connect(PLAYER);
        fixture.advance(Duration.ofSeconds(1));

        assertThat(results).isEmpty();
        assertThat(fixture.withdraw(STONE, Amount.of(1)).status()).isEqualTo(TransferStatus.INSUFFICIENT_STOCK);
    }
}
