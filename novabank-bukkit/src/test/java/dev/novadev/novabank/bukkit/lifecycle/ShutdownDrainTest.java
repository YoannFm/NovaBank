package dev.novadev.novabank.bukkit.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.bukkit.scheduler.BukkitTaskScheduler;
import dev.novadev.novabank.bukkit.scheduler.FakeBukkitScheduler;
import dev.novadev.novabank.bukkit.scheduler.IoExecutor;
import dev.novadev.novabank.bukkit.testing.RecordingLogger;
import dev.novadev.novabank.core.application.FlowDrain;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Phase d'arret reelle cote Bukkit, avec un planificateur qui refuse toute tache une fois
 * le plugin desactive (comme CraftScheduler) : une operation dont l'etape suivante est
 * soumise depuis l'executeur IO pendant l'arret se termine quand meme, par la pompe.
 */
class ShutdownDrainTest {

    private static final UUID PLAYER = UUID.randomUUID();

    private final RecordingLogger log = new RecordingLogger();
    private final FakeBukkitScheduler server = new FakeBukkitScheduler();
    private final IoExecutor io = new IoExecutor(2, 10, Duration.ofSeconds(5), log.logger());
    private final BukkitTaskScheduler scheduler = new BukkitTaskScheduler(server.plugin, server.scheduler, io,
            Set.of(PLAYER)::contains, log.logger());
    private final FlowDrain drain = new FlowDrain();
    private final List<String> events = new CopyOnWriteArrayList<>();

    @AfterEach
    void closeExecutor() {
        io.close();
    }

    @Test
    void operation_en_cours_terminee_par_la_pompe_apres_desactivation() {
        assertThat(drain.tryStart()).isTrue();
        scheduler.runForPlayer(PLAYER, () -> {
            events.add("etape joueur");
            scheduler.runAsync(() -> {
                sleep(150);
                events.add("etape IO");
                scheduler.runForPlayer(PLAYER, () -> {
                    events.add("fin");
                    drain.finished();
                }, () -> { });
            });
        }, () -> { });
        server.disablePlugin();

        new ShutdownDrain(drain, scheduler, () -> Duration.ofSeconds(5), log.logger()).close();

        assertThat(events).containsExactly("etape joueur", "etape IO", "fin");
        assertThat(drain.activeFlows()).isZero();
        assertThat(drain.tryStart()).as("nouvelles operations refusees").isFalse();
        assertThat(log.messages(Level.INFO)).anySatisfy(line -> assertThat(line).contains("terminees"));
        assertThat(log.messages(Level.SEVERE)).isEmpty();
    }

    @Test
    void sans_operation_en_cours_arret_silencieux() {
        server.disablePlugin();

        new ShutdownDrain(drain, scheduler, () -> Duration.ofSeconds(5), log.logger()).close();

        assertThat(log.messages()).isEmpty();
    }

    @Test
    void operation_bloquee_delai_depasse_signale_en_severe() {
        assertThat(drain.tryStart()).isTrue();
        server.disablePlugin();
        long start = System.nanoTime();

        new ShutdownDrain(drain, scheduler, () -> Duration.ofMillis(200), log.logger()).close();

        assertThat((System.nanoTime() - start) / 1_000_000L).isBetween(150L, 3_000L);
        assertThat(log.messages(Level.SEVERE)).singleElement()
                .satisfies(line -> assertThat(line).contains("1 operation(s) non terminee(s)"));
        scheduler.runGlobal(() -> events.add("apres"));
        assertThat(events).isEmpty();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
