package dev.novadev.novabank.core.application;

import static dev.novadev.novabank.core.application.ApplicationFixture.PERSONAL;
import static dev.novadev.novabank.core.application.ApplicationFixture.PLAYER;
import static dev.novadev.novabank.core.application.ApplicationFixture.STONE;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Maintenance (migration du stockage) : nouveaux flux refuses, flux en cours termines
 * normalement, reprise.
 */
class MaintenanceTest {

    private final ApplicationFixture fixture = new ApplicationFixture();

    @Test
    void pendant_la_maintenance_les_nouveaux_flux_sont_refuses_sans_toucher_a_l_inventaire() {
        fixture.inventory.put(PLAYER, STONE, 10);
        assertThat(fixture.drain.pause()).isTrue();
        assertThat(fixture.drain.pause()).as("maintenance deja en cours").isFalse();

        TransferResult result = fixture.deposit(ItemChoice.of(STONE), Amount.of(5)).get(0);

        assertThat(result.status()).isEqualTo(TransferStatus.MAINTENANCE);
        assertThat(fixture.inventory.quantity(PLAYER, STONE)).isEqualTo(10);
        assertThat(fixture.drain.activeFlows()).isZero();
        assertThat(fixture.drain.isShuttingDown()).isFalse();
    }

    @Test
    void un_flux_en_cours_se_termine_normalement_puis_la_maintenance_est_au_repos() throws Exception {
        fixture.inventory.put(PLAYER, STONE, 10);
        List<List<TransferResult>> received = new ArrayList<>();
        fixture.service.deposit(PLAYER, ItemChoice.of(STONE), Amount.of(10), received::add);
        fixture.drain.pause();

        assertThat(fixture.drain.awaitIdle(Duration.ofMillis(60))).as("flux encore en cours").isFalse();
        fixture.scheduler.runAll();

        assertThat(fixture.drain.awaitIdle(Duration.ofMillis(60))).isTrue();
        assertThat(received).hasSize(1);
        assertThat(received.get(0).get(0).status()).isEqualTo(TransferStatus.SUCCESS);
        assertThat(fixture.stock(PERSONAL, STONE)).isEqualTo(10);
    }

    @Test
    void apres_la_reprise_les_flux_sont_de_nouveau_acceptes() {
        fixture.inventory.put(PLAYER, STONE, 10);
        fixture.drain.pause();
        fixture.drain.resume();

        TransferResult result = fixture.deposit(ItemChoice.of(STONE), Amount.of(5)).get(0);

        assertThat(result.status()).isEqualTo(TransferStatus.SUCCESS);
        assertThat(fixture.drain.isPaused()).isFalse();
    }
}
