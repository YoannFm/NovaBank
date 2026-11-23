package dev.novadev.novabank.bukkit.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.novadev.novabank.bukkit.lifecycle.RemainingThreads;
import dev.novadev.novabank.bukkit.testing.RecordingLogger;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import org.junit.jupiter.api.Test;

class IoExecutorTest {

    private final RecordingLogger log = new RecordingLogger();

    @Test
    void les_threads_sont_nommes_et_bornes() throws InterruptedException {
        List<String> names = new CopyOnWriteArrayList<>();
        CountDownLatch done = new CountDownLatch(20);
        try (IoExecutor executor = new IoExecutor(2, 100, Duration.ofSeconds(5), log.logger())) {
            for (int i = 0; i < 20; i++) {
                executor.execute(() -> {
                    names.add(Thread.currentThread().getName());
                    done.countDown();
                });
            }
            assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(executor.stats().maxThreads()).isEqualTo(2);
        }
        assertThat(names).allSatisfy(name -> assertThat(name).matches("NovaBank-IO-[12]"));
    }

    @Test
    void une_file_pleine_refuse_la_soumission() throws InterruptedException {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);
        try (IoExecutor executor = new IoExecutor(1, 1, Duration.ofSeconds(5), log.logger())) {
            executor.execute(() -> {
                started.countDown();
                await(release);
            });
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            executor.execute(() -> { });

            assertThatThrownBy(() -> executor.execute(() -> { })).isInstanceOf(RejectedExecutionException.class);
            IoStats stats = executor.stats();
            assertThat(stats.active()).isEqualTo(1);
            assertThat(stats.queued()).isEqualTo(1);
            assertThat(stats.queueCapacity()).isEqualTo(1);
            release.countDown();
        }
    }

    @Test
    void la_fermeture_attend_les_taches_en_file() {
        AtomicInteger completed = new AtomicInteger();
        IoExecutor executor = new IoExecutor(1, 10, Duration.ofSeconds(5), log.logger());
        for (int i = 0; i < 5; i++) {
            executor.execute(() -> {
                sleep(20);
                completed.incrementAndGet();
            });
        }

        executor.close();

        assertThat(completed).hasValue(5);
        assertThat(executor.stats().shutdown()).isTrue();
        assertThatThrownBy(() -> executor.execute(() -> { })).isInstanceOf(RejectedExecutionException.class);
        assertThat(log.messages(Level.WARNING)).isEmpty();
    }

    @Test
    void au_dela_du_delai_les_taches_sont_interrompues_et_signalees() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        IoExecutor executor = new IoExecutor(1, 10, Duration.ofMillis(200), log.logger());
        executor.execute(() -> {
            started.countDown();
            sleep(60_000);
        });
        executor.execute(() -> { });
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

        executor.close();

        assertThat(log.messages(Level.WARNING)).singleElement()
                .satisfies(message -> assertThat(message).contains("1 tache(s) en attente abandonnee(s)"));
        assertThat(RemainingThreads.named(IoExecutor.THREAD_PREFIX)).isEmpty();
    }

    @Test
    void une_tache_en_echec_est_journalisee_et_l_executeur_continue() throws InterruptedException {
        CountDownLatch after = new CountDownLatch(1);
        try (IoExecutor executor = new IoExecutor(1, 10, Duration.ofSeconds(5), log.logger())) {
            executor.execute(() -> {
                throw new IllegalStateException("panne simulee");
            });
            executor.execute(after::countDown);
            assertThat(after.await(5, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(log.messages(Level.SEVERE)).singleElement()
                .satisfies(message -> assertThat(message).contains("panne simulee"));
    }

    @Test
    void parametres_invalides_refuses() {
        assertThatThrownBy(() -> new IoExecutor(0, 1, Duration.ofSeconds(1), log.logger()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IoExecutor(1, 0, Duration.ofSeconds(1), log.logger()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
