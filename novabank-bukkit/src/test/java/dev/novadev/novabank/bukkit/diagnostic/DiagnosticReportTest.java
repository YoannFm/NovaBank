package dev.novadev.novabank.bukkit.diagnostic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.novadev.novabank.bukkit.platform.PlatformDetector;
import dev.novadev.novabank.bukkit.platform.ServerEnvironment;
import dev.novadev.novabank.bukkit.scheduler.IoStats;
import dev.novadev.novabank.bukkit.testing.TestLanguages;
import dev.novadev.novabank.config.model.StorageType;
import dev.novadev.novabank.config.validation.Severity;
import dev.novadev.novabank.config.validation.ValidationIssue;
import dev.novadev.novabank.core.sync.SyncBusStatus;
import dev.novadev.novabank.core.sync.SyncStats;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.bukkit.ChatColor;
import org.junit.jupiter.api.Test;

class DiagnosticReportTest {

    private static final ServerEnvironment PAPER = ServerEnvironment.detect("1.21.11-R0.1-SNAPSHOT",
            "git-Paper-132 (MC: 1.21.11)", Set.of(PlatformDetector.PAPER_MARKER)::contains);

    @Test
    void le_rapport_contient_chaque_information_dans_l_ordre() {
        List<String> lines = plain(DiagnosticReport.lines(snapshot(DatabaseStatus.reachable(1, 3), List.of(
                new ValidationIssue(Severity.WARNING, "items[2].key", "item absent de cette version"))),
                TestLanguages.french()));

        assertThat(lines).hasSize(14);
        assertThat(lines.get(1)).contains("NovaBank 1.0.0-SNAPSHOT");
        assertThat(lines.get(2)).contains("Minecraft 1.21.11 [1.21.11-R0.1-SNAPSHOT]", "Paper", "git-Paper-132");
        assertThat(lines.get(3)).contains("Java : " + Runtime.version());
        assertThat(lines.get(4)).contains("SQLITE", "OK", "schema v1", "3 ms");
        assertThat(lines.get(5)).contains("sqlite 3.53.4.0", "3.53");
        assertThat(lines.get(6)).contains("Items acceptes : 5");
        assertThat(lines.get(7)).contains("Avertissements de configuration : 1");
        assertThat(lines.get(8)).contains("[AVERTISSEMENT] items[2].key : item absent de cette version");
        assertThat(lines.get(9)).contains("1/2 vivants", "0 actif(s)", "0/1000 en file", "7 tache(s) terminee(s)");
        assertThat(lines.get(10)).contains("Synchronisation", "desactivee");
        assertThat(lines.get(11)).contains("Classements", "aucun");
    }

    @Test
    void la_synchronisation_active_est_detaillee_sans_secret() {
        SyncStats stats = new SyncStats("lobby-1-a1b2c3d4", 12, 1, 7, 2, Optional.of(Instant.parse("2026-10-28T12:00:00Z")),
                Optional.empty());
        List<String> connected = plain(DiagnosticReport.lines(withSync(new SyncDiagnostic(stats,
                Optional.of(new SyncBusStatus(true, "redis.local:6379", "")))), TestLanguages.french()));
        List<String> down = plain(DiagnosticReport.lines(withSync(new SyncDiagnostic(
                new SyncStats("lobby-1-a1b2c3d4", 0, 3, 0, 0, Optional.empty(), Optional.of("StorageException : base coupee")),
                Optional.of(new SyncBusStatus(false, "redis.local:6379", "ConnectException : refused")))),
                TestLanguages.french()));

        assertThat(connected.get(9)).contains("lobby-1-a1b2c3d4", "OK", "redis.local:6379", "publies 12", "refuses 1",
                "via Redis 7", "via la base 2");
        assertThat(down.get(9)).contains("COUPE", "ConnectException : refused", "ERREUR", "base coupee", "refuses 3");
    }

    @Test
    void une_base_inaccessible_est_signalee() {
        List<String> lines = plain(DiagnosticReport.lines(
                snapshot(DatabaseStatus.unreachable("base verrouillee", 10_000), List.of()), TestLanguages.french()));

        assertThat(lines).hasSize(13);
        assertThat(lines.get(4)).contains("INACCESSIBLE", "base verrouillee");
    }

    @Test
    void un_statut_de_base_est_soit_une_version_soit_une_erreur() {
        assertThatThrownBy(() -> new DatabaseStatus(Optional.of(1), Optional.of("x"), 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DatabaseStatus(Optional.empty(), Optional.empty(), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static DiagnosticSnapshot snapshot(DatabaseStatus database, List<ValidationIssue> warnings) {
        return new DiagnosticSnapshot("1.0.0-SNAPSHOT", PAPER, StorageType.SQLITE, database, "sqlite", "3.53.4.0",
                "JDBC 3.53", 5, warnings, new IoStats(1, 2, 0, 0, 1000, 7, false), Optional.empty(), Optional.empty(), List.of(), List.of());
    }

    private static DiagnosticSnapshot withSync(SyncDiagnostic sync) {
        return new DiagnosticSnapshot("1.0.0-SNAPSHOT", PAPER, StorageType.MARIADB, DatabaseStatus.reachable(3, 1),
                "mariadb", "3.5.11", "JDBC 3.5", 5, List.of(), new IoStats(1, 2, 0, 0, 1000, 7, false),
                Optional.of(sync), Optional.empty(), List.of(), List.of());
    }

    private static List<String> plain(List<String> lines) {
        return lines.stream().map(ChatColor::stripColor).toList();
    }
}
