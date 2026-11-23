package dev.novadev.novabank.bukkit.command;

import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.bukkit.config.ReloadOutcome;
import dev.novadev.novabank.bukkit.diagnostic.DatabaseStatus;
import dev.novadev.novabank.bukkit.diagnostic.DiagnosticSnapshot;
import dev.novadev.novabank.bukkit.platform.ServerEnvironment;
import dev.novadev.novabank.bukkit.scheduler.IoStats;
import dev.novadev.novabank.bukkit.testing.RecordingLogger;
import dev.novadev.novabank.bukkit.testing.TestLanguages;
import dev.novadev.novabank.config.model.StorageType;
import dev.novadev.novabank.config.validation.ValidationReport;
import dev.novadev.novabank.core.application.ScheduledTask;
import dev.novadev.novabank.core.application.TaskScheduler;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class ScheduledAdminActionsTest {

    private final List<String> contexts = new ArrayList<>();
    private final RecordingLogger log = new RecordingLogger();
    private boolean saturated;

    private final TaskScheduler inline = new TaskScheduler() {
        @Override
        public void runGlobal(Runnable task) {
            contexts.add("global");
            task.run();
        }

        @Override
        public void runForPlayer(UUID playerId, Runnable task, Runnable ifOffline) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void runAsync(Runnable task) {
            if (saturated) {
                throw new RejectedExecutionException("plein");
            }
            contexts.add("async");
            task.run();
        }

        @Override
        public ScheduledTask runLaterGlobal(Runnable task, Duration delay) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledTask runRepeatingAsync(Runnable task, Duration initialDelay, Duration period) {
            throw new UnsupportedOperationException();
        }
    };

    @Test
    void le_diagnostic_est_collecte_en_asynchrone_puis_envoye_en_global() {
        RecordingActor admin = new RecordingActor(true);

        actions(this::snapshot, () -> null).diagnostic(admin);

        assertThat(contexts).containsExactly("async", "global");
        assertThat(admin.received().get(0)).contains("Diagnostic en cours");
        assertThat(admin.received()).anySatisfy(line -> assertThat(line).contains("SQLITE - OK"));
    }

    @Test
    void un_diagnostic_en_echec_est_signale_sans_exception() {
        RecordingActor admin = new RecordingActor(true);

        actions(() -> {
            throw new IllegalStateException("pilote decharge");
        }, () -> null).diagnostic(admin);

        assertThat(admin.received()).last().satisfies(line -> assertThat(line).contains("pilote decharge"));
    }

    @Test
    void executeur_sature_le_diagnostic_est_refuse_proprement() {
        saturated = true;
        RecordingActor admin = new RecordingActor(true);

        actions(this::snapshot, () -> null).diagnostic(admin);

        assertThat(admin.received()).last().satisfies(line -> assertThat(line).contains("sature"));
    }

    @Test
    void le_rechargement_envoie_le_rapport_et_le_journalise() {
        RecordingActor admin = new RecordingActor(true);
        ValidationReport report = ValidationReport.builder().warning("items[0].key", "item absent").build();

        actions(this::snapshot, () -> new ReloadOutcome(true, report, true, 4)).reload(admin);

        assertThat(admin.received()).containsExactly("NovaBank » Rechargement de config.yml...",
                "[AVERTISSEMENT] items[0].key : item absent",
                "NovaBank » Configuration rechargee : 4 item(s) accepte(s), 1 avertissement(s).",
                "NovaBank » Stockage, commandes ou politique de retrait modifies : redemarrage necessaire pour les"
                        + " appliquer.");
        assertThat(log.messages()).containsExactly("[AVERTISSEMENT] items[0].key : item absent",
                "NovaBank » Configuration rechargee : 4 item(s) accepte(s), 1 avertissement(s).",
                "NovaBank » Stockage, commandes ou politique de retrait modifies : redemarrage necessaire pour les"
                        + " appliquer.");
    }

    @Test
    void depuis_la_console_le_rapport_n_est_pas_journalise_deux_fois() {
        RecordingActor console = new RecordingActor(true, true);

        actions(this::snapshot, () -> new ReloadOutcome(true, ValidationReport.builder().build(), false, 5))
                .reload(console);

        assertThat(console.received()).contains("NovaBank » Configuration rechargee : 5 item(s) accepte(s), "
                + "0 avertissement(s).");
        assertThat(log.messages()).isEmpty();
    }

    @Test
    void un_rechargement_refuse_l_indique() {
        RecordingActor admin = new RecordingActor(true);
        ValidationReport report = ValidationReport.ofError("items[1].price", "le prix doit etre positif");

        actions(this::snapshot, () -> new ReloadOutcome(false, report, false, 5)).reload(admin);

        assertThat(admin.received()).contains("[ERREUR] items[1].price : le prix doit etre positif",
                "NovaBank » Rechargement refuse : 1 erreur(s). La configuration precedente reste en vigueur.");
    }

    private ScheduledAdminActions actions(Supplier<DiagnosticSnapshot> diagnostics, Supplier<ReloadOutcome> reload) {
        return new ScheduledAdminActions(inline, diagnostics, reload, TestLanguages::french, log.logger());
    }

    private DiagnosticSnapshot snapshot() {
        return new DiagnosticSnapshot("1.0.0", ServerEnvironment.detect("1.21.11-R0.1-SNAPSHOT", "test",
                Set.<String>of()::contains), StorageType.SQLITE, DatabaseStatus.reachable(1, 1), "sqlite", "3.53.4.0",
                "JDBC 3.53", 5, List.of(), new IoStats(0, 2, 0, 0, 1000, 0, false), java.util.Optional.empty(), java.util.Optional.empty(), List.of(), List.of());
    }
}
