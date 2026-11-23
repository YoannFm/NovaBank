package dev.novadev.novabank.bukkit.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.bukkit.testing.RecordingLogger;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class ShutdownTasksTest {

    private final ShutdownTasks tasks = new ShutdownTasks(new RecordingLogger().logger());
    private final List<String> events = new CopyOnWriteArrayList<>();

    @Test
    void la_pompe_reprend_les_taches_suivies_dans_l_ordre_de_soumission_sous_leur_forme_arret()
            throws InterruptedException {
        for (int i = 0; i < 20; i++) {
            String name = "t" + i;
            tasks.track(() -> events.add(name + " serveur"), () -> events.add(name));
        }

        tasks.startDraining();
        tasks.runPending(Duration.ZERO);

        assertThat(events).hasSize(20).allSatisfy(event -> assertThat(event).doesNotContain("serveur"));
        assertThat(events.get(0)).isEqualTo("t0");
        assertThat(events.get(19)).isEqualTo("t19");
    }

    @Test
    void une_tache_suivie_n_est_executee_qu_une_fois_quelle_que_soit_sa_forme() throws InterruptedException {
        ShutdownTasks.Tracked tracked = tasks.track(() -> events.add("serveur"), () -> events.add("arret"));
        tracked.runInstead(() -> events.add("joueur parti"));
        tracked.run();

        tasks.startDraining();
        tasks.runPending(Duration.ZERO);

        assertThat(events).containsExactly("joueur parti");
    }

    @Test
    void a_la_fermeture_les_taches_jamais_executees_sont_comptees_une_fois() {
        tasks.track(() -> events.add("suivie"), () -> events.add("suivie arret"));
        tasks.track(() -> events.add("executee"), () -> events.add("executee arret")).run();
        tasks.startDraining();
        tasks.offer(() -> events.add("en file"));

        assertThat(tasks.close()).isEqualTo(2);
        assertThat(tasks.offer(() -> events.add("apres"))).isFalse();
        assertThat(events).containsExactly("executee");
    }
}
