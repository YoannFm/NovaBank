package dev.novadev.novabank.bukkit.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.novadev.novabank.bukkit.testing.RecordingLogger;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.junit.jupiter.api.Test;

class LifecycleTest {

    private final RecordingLogger log = new RecordingLogger();
    private final List<String> closed = new ArrayList<>();

    @Test
    void les_ressources_sont_fermees_dans_l_ordre_inverse() {
        Lifecycle lifecycle = new Lifecycle(log.logger());
        lifecycle.register("pilote", () -> closed.add("pilote"));
        lifecycle.register("stockage", () -> closed.add("stockage"));
        lifecycle.register("executeur", () -> closed.add("executeur"));

        lifecycle.close();

        assertThat(closed).containsExactly("executeur", "stockage", "pilote");
    }

    @Test
    void un_echec_de_fermeture_est_journalise_et_n_empeche_pas_les_suivantes() {
        Lifecycle lifecycle = new Lifecycle(log.logger());
        lifecycle.register("pilote", () -> closed.add("pilote"));
        lifecycle.register("stockage", () -> {
            throw new IllegalStateException("disque plein");
        });
        lifecycle.register("executeur", () -> closed.add("executeur"));

        lifecycle.close();

        assertThat(closed).containsExactly("executeur", "pilote");
        assertThat(log.messages(Level.SEVERE)).singleElement()
                .satisfies(message -> assertThat(message).contains("stockage", "disque plein"));
    }

    @Test
    void la_fermeture_est_idempotente_et_refuse_les_nouvelles_ressources() {
        Lifecycle lifecycle = new Lifecycle(log.logger());
        lifecycle.register("pilote", () -> closed.add("pilote"));
        lifecycle.close();
        lifecycle.close();

        assertThatThrownBy(() -> lifecycle.register("tardive", () -> closed.add("tardive")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(closed).containsExactly("pilote", "tardive");
    }

    @Test
    void la_verification_des_threads_trouve_un_thread_vivant_par_son_prefixe() throws InterruptedException {
        Object lock = new Object();
        Thread thread = new Thread(() -> {
            synchronized (lock) {
                try {
                    lock.wait();
                } catch (InterruptedException stop) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "NovaBankTest-attente");
        thread.start();
        try {
            assertThat(RemainingThreads.named("NovaBankTest-")).containsExactly("NovaBankTest-attente");
        } finally {
            thread.interrupt();
            thread.join(5_000);
        }
        assertThat(RemainingThreads.named("NovaBankTest-")).isEmpty();
    }
}
