package dev.novadev.novabank.core.application;

import static dev.novadev.novabank.core.application.ApplicationFixture.PERSONAL;
import static dev.novadev.novabank.core.application.ApplicationFixture.PLAYER;
import static dev.novadev.novabank.core.application.ApplicationFixture.START;
import static dev.novadev.novabank.core.application.ApplicationFixture.STONE;
import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.api.OperationId;
import dev.novadev.novabank.core.delivery.PendingDelivery;
import dev.novadev.novabank.core.delivery.UndeliveredPolicy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeliveryAndReconciliationTest {

    private ApplicationFixture fixture = new ApplicationFixture();

    @Test
    void les_livraisons_en_attente_sont_remises_a_la_connexion_et_le_reste_conserve() {
        OperationId first = OperationId.random();
        fixture.deliveries.add(new PendingDelivery(first, PLAYER, STONE, 10));
        fixture.deliveries.add(new PendingDelivery(OperationId.random(), PLAYER, STONE, 5));
        fixture.inventory.capacity(PLAYER, 12);
        List<DeliveryReport> reports = new ArrayList<>();

        fixture.service.deliverPending(PLAYER, reports::add);
        fixture.scheduler.runAll();

        assertThat(reports).containsExactly(new DeliveryReport(12, 3));
        assertThat(fixture.inventory.quantity(PLAYER, STONE)).isEqualTo(12);
        assertThat(fixture.deliveries.pendingFor(PLAYER)).extracting(PendingDelivery::quantity).containsExactly(3L);
        assertThat(fixture.log.indexOf("sauvegarde")).isGreaterThan(fixture.log.indexOf("inventaire:don"));
    }

    @Test
    void rien_a_remettre_ou_operation_en_cours() {
        List<DeliveryReport> reports = new ArrayList<>();
        fixture.service.deliverPending(PLAYER, reports::add);
        fixture.scheduler.runAll();
        assertThat(reports).containsExactly(DeliveryReport.NOTHING);

        fixture.deliveries.add(new PendingDelivery(OperationId.random(), PLAYER, STONE, 2));
        fixture.service.deposit(PLAYER, ItemChoice.of(STONE), Amount.of(1), results -> { });
        fixture.service.deliverPending(PLAYER, reports::add);
        fixture.scheduler.runAll();
        assertThat(reports).hasSize(1);
    }

    @Test
    void la_reconciliation_solde_les_retraits_reserves_avant_le_demarrage_avec_zero_remis() {
        fixture = new ApplicationFixture(UndeliveredPolicy.RETURN_TO_BANK);
        fixture.inventory.put(PLAYER, STONE, 20);
        fixture.depositOne(STONE, 20);
        BankId bank = BankId.forOwner(PERSONAL);
        OperationId orphan = OperationId.random();
        fixture.ledger.reserveWithdrawal(orphan, bank, STONE, 8, PLAYER);
        assertThat(fixture.stock(PERSONAL, STONE)).isEqualTo(12);

        ReconciliationReport report = new WithdrawalReconciler(fixture.ledger, fixture.repository)
                .reconcile(START.plusSeconds(1));

        assertThat(report).isEqualTo(new ReconciliationReport(1, 0, 8));
        assertThat(fixture.stock(PERSONAL, STONE)).isEqualTo(20);
        assertThat(fixture.repository.direct().reservedWithdrawals()).isEmpty();
    }

    @Test
    void la_reconciliation_ignore_les_retraits_commences_apres_le_demarrage() {
        fixture.inventory.put(PLAYER, STONE, 20);
        fixture.depositOne(STONE, 20);
        fixture.ledger.reserveWithdrawal(OperationId.random(), BankId.forOwner(PERSONAL), STONE, 8, PLAYER);

        ReconciliationReport report = new WithdrawalReconciler(fixture.ledger, fixture.repository).reconcile(START);

        assertThat(report).isEqualTo(new ReconciliationReport(0, 0, 0));
        assertThat(fixture.repository.direct().reservedWithdrawals()).hasSize(1);
    }

    @Test
    void sur_un_stockage_partage_seuls_les_retraits_orphelins_sont_soldes() {
        fixture.inventory.put(PLAYER, STONE, 20);
        fixture.depositOne(STONE, 20);
        fixture.ledger.reserveWithdrawal(OperationId.random(), BankId.forOwner(PERSONAL), STONE, 8, PLAYER);
        WithdrawalReconciler reconciler = new WithdrawalReconciler(fixture.ledger, fixture.repository);

        // Reserve il y a une minute : peut etre en cours sur un autre serveur.
        assertThat(reconciler.reconcileStale(START.plus(Duration.ofMinutes(1))))
                .isEqualTo(new ReconciliationReport(0, 0, 0));
        assertThat(fixture.repository.direct().reservedWithdrawals()).hasSize(1);

        assertThat(reconciler.reconcileStale(START.plus(WithdrawalReconciler.SHARED_STORAGE_GRACE).plusSeconds(1)))
                .isEqualTo(new ReconciliationReport(1, 0, 8));
        assertThat(fixture.repository.direct().reservedWithdrawals()).isEmpty();
    }

    @Test
    void la_reconciliation_avec_livraison_differee_cree_la_livraison() {
        fixture.inventory.put(PLAYER, STONE, 20);
        fixture.depositOne(STONE, 20);
        OperationId orphan = OperationId.random();
        fixture.ledger.reserveWithdrawal(orphan, BankId.forOwner(PERSONAL), STONE, 8, PLAYER);

        new WithdrawalReconciler(fixture.ledger, fixture.repository).reconcile(START.plusSeconds(1));

        assertThat(fixture.deliveries.find(orphan)).hasValueSatisfying(delivery ->
                assertThat(delivery.quantity()).isEqualTo(8));
    }
}
