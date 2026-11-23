package dev.novadev.novabank.bukkit.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.bukkit.testing.RecordingLogger;
import dev.novadev.novabank.core.application.FlowDrain;
import dev.novadev.novabank.core.application.ScheduledTask;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class BukkitTaskSchedulerTest {

    private static final UUID ONLINE = UUID.randomUUID();
    private static final UUID OFFLINE = UUID.randomUUID();

    private final RecordingLogger log = new RecordingLogger();
    private final FakeBukkitScheduler server = new FakeBukkitScheduler();
    private final IoExecutor io = new IoExecutor(2, 10, Duration.ofSeconds(5), log.logger());
    private final BukkitTaskScheduler scheduler = new BukkitTaskScheduler(server.plugin, server.scheduler, io,
            Set.of(ONLINE)::contains, log.logger());
    private final List<String> events = new CopyOnWriteArrayList<>();

    @AfterEach
    void closeExecutor() {
        io.close();
    }

    @Test
    void global_et_joueur_passent_par_le_thread_principal() {
        scheduler.runGlobal(() -> events.add("global"));
        scheduler.runForPlayer(ONLINE, () -> events.add("joueur present"), () -> events.add("repli present"));
        scheduler.runForPlayer(OFFLINE, () -> events.add("joueur parti"), () -> events.add("repli parti"));
        assertThat(events).isEmpty();

        server.runAll();

        assertThat(events).containsExactly("global", "joueur present", "repli parti");
        assertThat(server.planned()).allSatisfy(task -> assertThat(task.delay()).isZero());
    }

    @Test
    void la_presence_du_joueur_est_evaluee_au_moment_de_l_execution() {
        Set<UUID> online = new HashSet<>(Set.of(ONLINE));
        BukkitTaskScheduler dynamic = new BukkitTaskScheduler(server.plugin, server.scheduler, io, online::contains,
                log.logger());
        dynamic.runForPlayer(ONLINE, () -> events.add("joueur"), () -> events.add("repli"));
        online.clear();

        server.runAll();

        assertThat(events).containsExactly("repli");
    }

    @Test
    void les_taches_asynchrones_tournent_sur_l_executeur_io() throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        scheduler.runAsync(() -> {
            events.add(Thread.currentThread().getName());
            done.countDown();
        });

        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(events).singleElement().satisfies(name -> assertThat(name).startsWith(IoExecutor.THREAD_PREFIX));
        assertThat(server.planned()).isEmpty();
    }

    @Test
    void delai_converti_en_ticks_et_tache_annulable() {
        ScheduledTask task = scheduler.runLaterGlobal(() -> events.add("plus tard"), Duration.ofMillis(120));

        assertThat(server.planned()).singleElement().satisfies(planned -> assertThat(planned.delay()).isEqualTo(3));
        task.cancel();
        server.runAll();

        assertThat(task.isCancelled()).isTrue();
        assertThat(events).isEmpty();
    }

    @Test
    void la_tache_periodique_s_execute_sur_l_executeur_sans_empilement() throws InterruptedException {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);
        AtomicInteger runs = new AtomicInteger();
        ScheduledTask task = scheduler.runRepeatingAsync(() -> {
            runs.incrementAndGet();
            events.add(Thread.currentThread().getName());
            started.countDown();
            awaitQuietly(release);
        }, Duration.ZERO, Duration.ofSeconds(1));

        assertThat(server.planned()).singleElement().satisfies(planned -> {
            assertThat(planned.delay()).isZero();
            assertThat(planned.period()).isEqualTo(20);
        });
        server.runAll();
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        server.runAll();
        server.runAll();
        assertThat(runs).hasValue(1);

        release.countDown();
        task.cancel();
        assertThat(task.isCancelled()).isTrue();
        assertThat(events).singleElement().satisfies(name -> assertThat(name).startsWith(IoExecutor.THREAD_PREFIX));
    }

    @Test
    void apres_desactivation_differees_et_periodiques_ignorees_globales_en_file_jusqu_a_la_fin_de_l_arret()
            throws InterruptedException {
        server.disablePlugin();

        scheduler.runGlobal(() -> events.add("global"));
        ScheduledTask later = scheduler.runLaterGlobal(() -> events.add("plus tard"), Duration.ofSeconds(1));
        ScheduledTask repeating = scheduler.runRepeatingAsync(() -> events.add("periodique"), Duration.ZERO,
                Duration.ofSeconds(1));

        assertThat(server.planned()).isEmpty();
        assertThat(later.isCancelled()).isTrue();
        assertThat(repeating.isCancelled()).isTrue();
        assertThat(log.messages(Level.WARNING)).hasSize(2);
        scheduler.startDraining().runPending(Duration.ZERO);
        assertThat(events).containsExactly("global");
        assertThat(scheduler.stopDraining()).isZero();
        scheduler.runGlobal(() -> events.add("apres l'arret"));
        assertThat(events).containsExactly("global");
        assertThat(log.messages(Level.WARNING)).hasSize(3);
    }

    @Test
    void taches_planifiees_avant_l_arret_executees_une_seule_fois_par_la_pompe() throws InterruptedException {
        scheduler.runGlobal(() -> events.add("global"));
        scheduler.runForPlayer(ONLINE, () -> events.add("joueur"), () -> events.add("repli"));
        server.disablePlugin();

        FlowDrain.Pump pump = scheduler.startDraining();
        pump.runPending(Duration.ZERO);
        server.runAll();
        pump.runPending(Duration.ZERO);

        assertThat(events).containsExactly("global", "joueur");
        assertThat(scheduler.stopDraining()).isZero();
    }

    @Test
    void tache_deja_executee_par_bukkit_non_rejouee_par_la_pompe() throws InterruptedException {
        scheduler.runGlobal(() -> events.add("global"));
        server.runAll();

        scheduler.startDraining().runPending(Duration.ZERO);

        assertThat(events).containsExactly("global");
    }

    @Test
    void desactivation_entre_verification_et_planification_tache_mise_en_file() throws InterruptedException {
        server.refuseTasksWhileEnabled();

        scheduler.runGlobal(() -> events.add("global"));

        assertThat(server.planned()).isEmpty();
        scheduler.startDraining().runPending(Duration.ZERO);
        assertThat(events).containsExactly("global");
    }

    @Test
    void la_pompe_attend_une_tache_soumise_depuis_l_executeur_io() throws InterruptedException {
        server.disablePlugin();
        FlowDrain.Pump pump = scheduler.startDraining();
        CountDownLatch submitted = new CountDownLatch(1);

        scheduler.runAsync(() -> {
            scheduler.runForPlayer(OFFLINE, () -> events.add("joueur"), () -> events.add("repli"));
            submitted.countDown();
        });
        assertThat(submitted.await(5, TimeUnit.SECONDS)).isTrue();
        pump.runPending(Duration.ofSeconds(5));

        assertThat(events).containsExactly("repli");
    }

    @Test
    void tache_restee_en_file_comptee_a_la_fin_de_l_arret_et_echec_d_une_tache_journalise()
            throws InterruptedException {
        server.disablePlugin();
        FlowDrain.Pump pump = scheduler.startDraining();
        scheduler.runGlobal(() -> {
            throw new IllegalStateException("panne");
        });
        pump.runPending(Duration.ZERO);
        scheduler.runGlobal(() -> events.add("jamais"));

        assertThat(scheduler.stopDraining()).isEqualTo(1);
        assertThat(events).isEmpty();
        assertThat(log.messages(Level.SEVERE)).singleElement()
                .satisfies(message -> assertThat(message).contains("panne"));
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
