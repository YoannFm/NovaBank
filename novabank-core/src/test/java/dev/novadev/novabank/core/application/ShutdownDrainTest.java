package dev.novadev.novabank.core.application;

import static dev.novadev.novabank.core.application.ApplicationFixture.PERSONAL;
import static dev.novadev.novabank.core.application.ApplicationFixture.PLAYER;
import static dev.novadev.novabank.core.application.ApplicationFixture.STONE;
import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.core.delivery.PendingDelivery;
import dev.novadev.novabank.core.delivery.UndeliveredPolicy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Phase d'arret : nouveaux flux refuses, flux en cours termines sans toucher a
 * l'inventaire, aucun item perdu ni duplique. Le "pompage" de la plateforme est simule par
 * le planificateur manuel : apres le debut de l'arret, seules les taches executees par la
 * pompe tournent (un planificateur Bukkit n'executerait plus rien).
 */
class ShutdownDrainTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private ApplicationFixture fixture = new ApplicationFixture();

    private boolean drain() {
        return fixture.drain.drain(TIMEOUT, maxWait -> fixture.scheduler.runAll());
    }

    @Test
    void sans_flux_en_cours_l_arret_est_immediat_et_les_nouveaux_flux_sont_refuses() {
        fixture.inventory.put(PLAYER, STONE, 10);

        assertThat(drain()).isTrue();
        TransferResult result = fixture.deposit(ItemChoice.of(STONE), Amount.of(5)).get(0);

        assertThat(result.status()).isEqualTo(TransferStatus.SHUTTING_DOWN);
        assertThat(fixture.inventory.quantity(PLAYER, STONE)).isEqualTo(10);
        assertThat(fixture.drain.activeFlows()).isZero();
        assertThat(fixture.drain.tryStart()).isFalse();
    }

    @Test
    void un_retrait_demande_pendant_l_arret_est_refuse_et_libere_le_verrou() {
        fixture.inventory.put(PLAYER, STONE, 10);
        fixture.depositOne(STONE, 10);
        assertThat(drain()).isTrue();

        TransferResult result = fixture.withdraw(STONE, Amount.of(1));

        assertThat(result.status()).isEqualTo(TransferStatus.SHUTTING_DOWN);
        assertThat(fixture.stock(PERSONAL, STONE)).isEqualTo(10);
        assertThat(fixture.drain.activeFlows()).isZero();
    }

    @Test
    void depot_refuse_pendant_l_arret_les_items_deviennent_une_livraison_en_attente() {
        fixture.catalog.remove(STONE);
        fixture.inventory.put(PLAYER, STONE, 10);
        List<List<TransferResult>> received = new ArrayList<>();
        fixture.service.deposit(PLAYER, ItemChoice.of(STONE), Amount.of(10), received::add);
        runSteps(4); // acces, banque, retrait des items, enregistrement refuse par la banque

        assertThat(fixture.inventory.quantity(PLAYER, STONE)).isZero();
        assertThat(drain()).isTrue();

        assertThat(fixture.inventory.quantity(PLAYER, STONE)).as("inventaire non touche pendant l'arret").isZero();
        assertThat(fixture.deliveries.pendingFor(PLAYER)).extracting(PendingDelivery::quantity).containsExactly(10L);
        assertThat(received).singleElement().satisfies(results -> assertThat(results).singleElement()
                .satisfies(result -> assertThat(result.undelivered()).isEqualTo(10L)));
        assertThat(fixture.drain.activeFlows()).isZero();
    }

    @Test
    void depot_valide_pendant_l_arret_reste_en_banque_sans_retour_d_items() {
        fixture.inventory.put(PLAYER, STONE, 10);
        fixture.service.deposit(PLAYER, ItemChoice.of(STONE), Amount.of(10), results -> { });
        runSteps(3); // jusqu'au retrait des items ; l'enregistrement est en file

        assertThat(drain()).isTrue();

        assertThat(fixture.stock(PERSONAL, STONE)).isEqualTo(10);
        assertThat(fixture.inventory.quantity(PLAYER, STONE)).isZero();
        assertThat(fixture.deliveries.size()).isZero();
        assertThat(fixture.log.indexOf("sauvegarde")).as("pas de sauvegarde du joueur pendant l'arret").isNegative();
    }

    @Test
    void depot_interrompu_avant_le_retrait_des_items_ne_touche_pas_l_inventaire() {
        fixture.inventory.put(PLAYER, STONE, 10);
        List<List<TransferResult>> received = new ArrayList<>();
        fixture.service.deposit(PLAYER, ItemChoice.of(STONE), Amount.of(10), received::add);
        runSteps(2); // acces, banque : le retrait des items est en file

        assertThat(drain()).isTrue();

        assertThat(fixture.inventory.quantity(PLAYER, STONE)).isEqualTo(10);
        assertThat(received).singleElement().satisfies(results -> assertThat(results).singleElement()
                .satisfies(result -> assertThat(result.status()).isEqualTo(TransferStatus.SHUTTING_DOWN)));
    }

    @Test
    void retrait_reserve_pendant_l_arret_solde_en_livraison_en_attente() {
        reserveThenDrain();

        assertThat(fixture.inventory.quantity(PLAYER, STONE)).isZero();
        assertThat(fixture.stock(PERSONAL, STONE)).isEqualTo(15);
        assertThat(fixture.deliveries.pendingFor(PLAYER)).extracting(PendingDelivery::quantity).containsExactly(5L);
        assertThat(fixture.repository.direct().reservedWithdrawals()).isEmpty();
    }

    @Test
    void retrait_reserve_pendant_l_arret_rendu_a_la_banque_selon_la_politique() {
        fixture = new ApplicationFixture(UndeliveredPolicy.RETURN_TO_BANK);

        reserveThenDrain();

        assertThat(fixture.inventory.quantity(PLAYER, STONE)).isZero();
        assertThat(fixture.stock(PERSONAL, STONE)).isEqualTo(20);
        assertThat(fixture.deliveries.size()).isZero();
        assertThat(fixture.repository.direct().reservedWithdrawals()).isEmpty();
    }

    @Test
    void la_remise_des_livraisons_n_est_pas_lancee_pendant_l_arret() {
        fixture.deliveries.add(new PendingDelivery(dev.novadev.novabank.api.OperationId.random(), PLAYER, STONE, 3));
        assertThat(drain()).isTrue();
        List<DeliveryReport> reports = new ArrayList<>();

        fixture.service.deliverPending(PLAYER, reports::add);
        fixture.scheduler.runAll();

        assertThat(reports).isEmpty();
        assertThat(fixture.inventory.quantity(PLAYER, STONE)).isZero();
        assertThat(fixture.deliveries.pendingFor(PLAYER)).hasSize(1);
        assertThat(fixture.drain.activeFlows()).isZero();
    }

    @Test
    void remise_des_livraisons_en_cours_interrompue_sans_rien_donner() {
        fixture.deliveries.add(new PendingDelivery(dev.novadev.novabank.api.OperationId.random(), PLAYER, STONE, 3));
        fixture.service.deliverPending(PLAYER, report -> { });
        runSteps(1); // lecture des livraisons : la remise est en file

        assertThat(drain()).isTrue();

        assertThat(fixture.inventory.quantity(PLAYER, STONE)).isZero();
        assertThat(fixture.deliveries.pendingFor(PLAYER)).extracting(PendingDelivery::quantity).containsExactly(3L);
    }

    @Test
    void delai_depasse_si_les_taches_ne_sont_plus_executees() {
        fixture.inventory.put(PLAYER, STONE, 10);
        fixture.service.deposit(PLAYER, ItemChoice.of(STONE), Amount.of(10), results -> { });

        boolean drained = fixture.drain.drain(Duration.ofMillis(120), maxWait -> Thread.sleep(maxWait.toMillis()));

        assertThat(drained).isFalse();
        assertThat(fixture.drain.activeFlows()).isEqualTo(1);
    }

    @Test
    void attente_interrompue_signalee_comme_inachevee() {
        fixture.inventory.put(PLAYER, STONE, 10);
        fixture.service.deposit(PLAYER, ItemChoice.of(STONE), Amount.of(10), results -> { });

        boolean drained = fixture.drain.drain(TIMEOUT, maxWait -> {
            throw new InterruptedException("test");
        });

        assertThat(drained).isFalse();
        assertThat(Thread.interrupted()).isTrue();
    }

    @Test
    void un_flux_refuse_par_le_verrou_ou_le_delai_n_est_pas_compte() {
        fixture.inventory.put(PLAYER, STONE, 10);
        fixture.service.deposit(PLAYER, ItemChoice.of(STONE), Amount.of(1), results -> { });
        fixture.service.deposit(PLAYER, ItemChoice.of(STONE), Amount.of(1), results -> { });

        assertThat(fixture.drain.activeFlows()).isEqualTo(1);
        fixture.scheduler.runAll();
        assertThat(fixture.drain.activeFlows()).isZero();
        fixture.service.deposit(PLAYER, ItemChoice.of(STONE), Amount.of(1), results -> { });
        assertThat(fixture.drain.activeFlows()).as("delai entre deux operations").isZero();
    }

    private void reserveThenDrain() {
        fixture.inventory.put(PLAYER, STONE, 20);
        fixture.depositOne(STONE, 20);
        List<TransferResult> received = new ArrayList<>();
        fixture.service.withdraw(PLAYER, STONE, Amount.of(5), received::add);
        runSteps(2); // acces, reservation : la remise des items est en file
        assertThat(fixture.repository.direct().reservedWithdrawals()).hasSize(1);

        assertThat(drain()).isTrue();

        assertThat(received).singleElement().satisfies(result -> {
            assertThat(result.quantity()).isZero();
            assertThat(result.undelivered()).isEqualTo(5L);
        });
        assertThat(fixture.drain.activeFlows()).isZero();
    }

    private void runSteps(int steps) {
        for (int i = 0; i < steps; i++) {
            assertThat(fixture.scheduler.runOne()).isTrue();
        }
    }
}
