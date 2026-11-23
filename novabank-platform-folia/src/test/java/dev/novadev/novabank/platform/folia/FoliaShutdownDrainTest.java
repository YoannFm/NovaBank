package dev.novadev.novabank.platform.folia;

import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.bukkit.scheduler.IoExecutor;
import dev.novadev.novabank.bukkit.scheduler.PluginScheduler;
import dev.novadev.novabank.core.application.FlowDrain;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Phase d'arret sous Folia : les regions sont arretees AVANT la desactivation du plugin.
 * Une operation dont l'etape "joueur" a ete acceptee par Folia mais jamais executee, puis
 * dont l'etape suivante est soumise depuis l'executeur IO pendant l'arret, se termine par la
 * pompe ; comme dans les flux de NovaBank, l'etape "joueur" prend sa branche "joueur parti"
 * pendant l'arret.
 */
class FoliaShutdownDrainTest {

    private static final UUID PLAYER = UUID.randomUUID();

    private final RecordingLog log = new RecordingLog();
    private final FakeFolia folia = new FakeFolia();
    private final IoExecutor io = new IoExecutor(2, 10, Duration.ofSeconds(5), log.logger());
    private final PluginScheduler scheduler = new FoliaTaskScheduler(folia.plugin, folia.server(), io, log.logger());
    private final FlowDrain drain = new FlowDrain();
    private final List<String> events = new CopyOnWriteArrayList<>();

    @AfterEach
    void closeExecutor() {
        io.close();
    }

    @Test
    void operation_acceptee_par_folia_avant_l_arret_des_regions_terminee_par_la_pompe() {
        folia.join(PLAYER);
        folia.ownHere(PLAYER);
        assertThat(drain.tryStart()).isTrue();
        scheduler.runForPlayer(PLAYER, () -> playerStep("inventaire"), () -> events.add("parti"));

        // Arret Folia : regions arretees (plus aucun tick), puis desactivation du plugin.
        folia.disablePlugin();
        boolean done = drain.drain(Duration.ofSeconds(5), scheduler.startDraining());

        assertThat(done).isTrue();
        assertThat(events).containsExactly("livraison en attente", "IO", "resultat");
        assertThat(drain.tryStart()).as("nouvelles operations refusees").isFalse();
        assertThat(scheduler.stopDraining()).isZero();
    }

    @Test
    void operation_bloquee_delai_borne() {
        assertThat(drain.tryStart()).isTrue();
        folia.disablePlugin();
        long start = System.nanoTime();

        boolean done = drain.drain(Duration.ofMillis(200), scheduler.startDraining());

        assertThat(done).isFalse();
        assertThat((System.nanoTime() - start) / 1_000_000L).isBetween(150L, 3_000L);
    }

    /** Etape "joueur" d'un flux : pendant l'arret, l'inventaire n'est pas touche (voir Flow). */
    private void playerStep(String name) {
        if (!drain.isShuttingDown()) {
            events.add(name);
            return;
        }
        events.add("livraison en attente");
        scheduler.runAsync(() -> {
            sleep(100);
            events.add("IO");
            scheduler.runForPlayer(PLAYER, () -> {
                events.add("resultat");
                drain.finished();
            }, () -> {
                events.add("resultat perdu");
                drain.finished();
            });
        });
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
