package dev.novadev.novabank.platform.folia;

import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.bukkit.scheduler.IoExecutor;
import dev.novadev.novabank.bukkit.scheduler.PluginScheduler;
import dev.novadev.novabank.core.application.FlowDrain;
import dev.novadev.novabank.core.application.ScheduledTask;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class FoliaTaskSchedulerTest {

    private static final UUID PLAYER = UUID.randomUUID();

    private final RecordingLog log = new RecordingLog();
    private final FakeFolia folia = new FakeFolia();
    private final IoExecutor io = new IoExecutor(2, 10, Duration.ofSeconds(5), log.logger());
    private final PluginScheduler scheduler = new FoliaSchedulerFactory().create(folia.plugin, io, log.logger());
    private final List<String> events = new CopyOnWriteArrayList<>();

    @AfterEach
    void closeExecutor() {
        io.close();
    }

    @Test
    void la_fabrique_cree_le_planificateur_folia() {
        assertThat(scheduler).isInstanceOf(FoliaTaskScheduler.class);
    }

    @Test
    void global_sur_la_region_globale_jamais_sur_le_planificateur_bukkit() {
        scheduler.runGlobal(() -> events.add("global"));
        assertThat(events).isEmpty();

        folia.tickGlobal();

        assertThat(events).containsExactly("global");
        assertThat(folia.global()).singleElement().satisfies(task -> assertThat(task.delay).isEqualTo(1));
    }

    @Test
    void tache_joueur_sur_le_planificateur_de_son_entite() {
        folia.join(PLAYER);

        scheduler.runForPlayer(PLAYER, () -> events.add("joueur"), () -> events.add("parti"));
        folia.tickGlobal();
        assertThat(events).isEmpty();
        folia.tickPlayer(PLAYER);

        assertThat(events).containsExactly("joueur");
        assertThat(folia.global()).isEmpty();
    }

    @Test
    void joueur_deconnecte_avant_l_execution_branche_joueur_parti_par_le_rappel_retired() {
        folia.join(PLAYER);
        scheduler.runForPlayer(PLAYER, () -> events.add("joueur"), () -> events.add("parti"));

        folia.quit(PLAYER);
        folia.tickPlayer(PLAYER);

        assertThat(events).containsExactly("parti");
    }

    @Test
    void joueur_absent_branche_joueur_parti_sur_la_region_globale() {
        scheduler.runForPlayer(PLAYER, () -> events.add("joueur"), () -> events.add("parti"));
        assertThat(events).isEmpty();

        folia.tickGlobal();

        assertThat(events).containsExactly("parti");
    }

    @Test
    void entite_deja_retiree_null_rendu_par_folia_branche_joueur_parti_sur_la_region_globale() {
        folia.join(PLAYER);
        folia.retireSilently(PLAYER);

        scheduler.runForPlayer(PLAYER, () -> events.add("joueur"), () -> events.add("parti"));
        folia.tickGlobal();

        assertThat(events).containsExactly("parti");
    }

    @Test
    void acces_au_joueur_seulement_depuis_sa_region() {
        assertThat(scheduler.canAccessPlayer(PLAYER)).as("deconnecte").isFalse();
        folia.join(PLAYER);
        assertThat(scheduler.canAccessPlayer(PLAYER)).as("autre region").isFalse();
        folia.ownHere(PLAYER);
        assertThat(scheduler.canAccessPlayer(PLAYER)).isTrue();
    }

    @Test
    void delai_converti_en_ticks_delai_nul_porte_a_un_tick_et_tache_annulable() {
        ScheduledTask later = scheduler.runLaterGlobal(() -> events.add("plus tard"), Duration.ofMillis(120));
        ScheduledTask now = scheduler.runLaterGlobal(() -> events.add("tout de suite"), Duration.ZERO);

        assertThat(folia.global()).extracting(task -> task.delay).containsExactly(3L, 1L);
        later.cancel();
        folia.tickGlobal();

        assertThat(later.isCancelled()).isTrue();
        assertThat(now.isCancelled()).isFalse();
        assertThat(events).containsExactly("tout de suite");
    }

    @Test
    void tache_periodique_cadencee_par_la_region_globale_executee_sur_l_executeur_io_sans_empilement()
            throws InterruptedException {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);
        AtomicInteger runs = new AtomicInteger();
        ScheduledTask task = scheduler.runRepeatingAsync(() -> {
            runs.incrementAndGet();
            events.add(Thread.currentThread().getName());
            started.countDown();
            awaitQuietly(release);
        }, Duration.ZERO, Duration.ofSeconds(1));

        assertThat(folia.global()).singleElement().satisfies(planned -> {
            assertThat(planned.delay).isEqualTo(1);
            assertThat(planned.period).isEqualTo(20);
        });
        folia.tickGlobal();
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        folia.tickGlobal();
        folia.tickGlobal();
        assertThat(runs).hasValue(1);

        release.countDown();
        task.cancel();
        assertThat(task.isCancelled()).isTrue();
        assertThat(events).singleElement().satisfies(name -> assertThat(name).startsWith(IoExecutor.THREAD_PREFIX));
    }

    @Test
    void taches_acceptees_par_folia_puis_ignorees_a_la_desactivation_reprises_une_fois_par_la_pompe()
            throws InterruptedException {
        UUID elsewhere = UUID.randomUUID();
        folia.join(PLAYER);
        folia.ownHere(PLAYER);
        folia.join(elsewhere);
        scheduler.runGlobal(() -> events.add("global"));
        scheduler.runForPlayer(PLAYER, () -> events.add("joueur ici"), () -> events.add("parti ici"));
        scheduler.runForPlayer(elsewhere, () -> events.add("joueur ailleurs"), () -> events.add("parti ailleurs"));

        folia.disablePlugin();
        folia.tickGlobal();
        folia.tickPlayer(PLAYER);
        folia.tickPlayer(elsewhere);
        assertThat(events).as("Folia ignore les taches d'un plugin desactive").isEmpty();

        FlowDrain.Pump pump = scheduler.startDraining();
        pump.runPending(Duration.ZERO);
        pump.runPending(Duration.ZERO);

        assertThat(events).containsExactly("global", "joueur ici", "parti ailleurs");
        assertThat(scheduler.stopDraining()).isZero();
    }

    @Test
    void apres_desactivation_les_taches_vont_dans_la_file_d_arret_puis_sont_ignorees() throws InterruptedException {
        folia.join(PLAYER);
        folia.disablePlugin();

        scheduler.runForPlayer(PLAYER, () -> events.add("joueur"), () -> events.add("parti"));
        ScheduledTask later = scheduler.runLaterGlobal(() -> events.add("plus tard"), Duration.ofSeconds(1));
        assertThat(later.isCancelled()).isTrue();
        assertThat(folia.entity(PLAYER)).isEmpty();
        scheduler.startDraining().runPending(Duration.ZERO);
        assertThat(events).containsExactly("parti");

        assertThat(scheduler.stopDraining()).isZero();
        scheduler.runGlobal(() -> events.add("apres l'arret"));
        assertThat(events).containsExactly("parti");
        assertThat(log.messages(Level.WARNING)).hasSize(2);
    }

    @Test
    void tache_deja_executee_par_folia_non_rejouee_par_la_pompe() throws InterruptedException {
        folia.join(PLAYER);
        folia.ownHere(PLAYER);
        scheduler.runForPlayer(PLAYER, () -> events.add("joueur"), () -> events.add("parti"));
        folia.tickPlayer(PLAYER);
        folia.disablePlugin();

        scheduler.startDraining().runPending(Duration.ZERO);

        assertThat(events).containsExactly("joueur");
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
