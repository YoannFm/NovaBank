package dev.novadev.novabank.bukkit.diagnostic;

import dev.novadev.novabank.bukkit.message.Language;
import dev.novadev.novabank.bukkit.message.ValidationLines;
import dev.novadev.novabank.bukkit.platform.ServerEnvironment;
import dev.novadev.novabank.bukkit.scheduler.IoStats;
import dev.novadev.novabank.core.sync.SyncStats;
import dev.novadev.novabank.core.ranking.LeaderboardStats;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.List;
import java.util.Objects;

/**
 * Mise en forme du diagnostic, une ligne par information, dans la langue en vigueur.
 */
public final class DiagnosticReport {

    private DiagnosticReport() {
    }

    /**
     * @param snapshot etat collecte
     * @param language langue
     * @return lignes a envoyer, dans l'ordre
     */
    public static List<String> lines(DiagnosticSnapshot snapshot, Language language) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(language, "language");
        ServerEnvironment environment = snapshot.environment();
        List<String> lines = new ArrayList<>();
        lines.add(language.chat("admin.diagnostic.header"));
        lines.add(line(language, "plugin", "NovaBank " + snapshot.pluginVersion()));
        lines.add(line(language, "server", "Minecraft " + environment.versionLabel() + " [" + environment.rawVersion()
                + "], " + environment.platform().displayName() + " (" + environment.serverName() + ")"));
        lines.add(line(language, "java", environment.javaVersion().toString()));
        lines.add(line(language, "storage", snapshot.storageType() + " - " + snapshot.database().schemaVersion()
                .map(version -> language.text("admin.diagnostic.storage-ok", "schema", version,
                        "latency", snapshot.database().latencyMillis()))
                .orElseGet(() -> language.text("admin.diagnostic.storage-ko", "error",
                        snapshot.database().error().orElse("?")))));
        lines.add(line(language, "driver", language.text("admin.diagnostic.driver-info", "id", snapshot.driverId(),
                "artifact", snapshot.driverArtifact(), "detail", snapshot.driverDetail())));
        lines.add(line(language, "items", String.valueOf(snapshot.acceptedItems())));
        lines.add(line(language, "warnings", String.valueOf(snapshot.warnings().size())));
        snapshot.warnings().forEach(warning -> lines.add(ValidationLines.of(warning)));
        lines.add(line(language, "io", io(language, snapshot.io())));
        lines.add(line(language, "sync", snapshot.sync().map(sync -> sync(language, sync))
                .orElseGet(() -> language.text("admin.diagnostic.sync-off"))));
        lines.add(line(language, "leaderboard", snapshot.leaderboards().map(stats -> leaderboard(language, stats))
                .orElseGet(() -> language.text("admin.diagnostic.leaderboard-off"))));
        lines.add(line(language, "components", snapshot.components().isEmpty() ? "-"
                : String.join(", ", snapshot.components())));
        lines.add(line(language, "errors", String.valueOf(snapshot.recentErrors().size())));
        snapshot.recentErrors().forEach(error -> lines.add(language.text("admin.diagnostic.error-line", "error", error)));
        return List.copyOf(lines);
    }

    private static String leaderboard(Language language, LeaderboardStats stats) {
        String entries = stats.entries().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + " " + entry.getValue()).collect(Collectors.joining(", "));
        return language.text("admin.diagnostic.leaderboard-value", "entries", entries.isEmpty() ? "-" : entries,
                "last", stats.lastRebuild().map(Instant::toString)
                        .orElse(language.text("admin.diagnostic.leaderboard-never")),
                "took", stats.lastRebuildTook().map(Duration::toMillis).orElse(0L), "refreshes", stats.refreshes(),
                "error", stats.lastError().map(error -> language.text("admin.diagnostic.leaderboard-error", "error",
                        error)).orElse(""));
    }

    private static String sync(Language language, SyncDiagnostic sync) {
        SyncStats stats = sync.stats();
        String bus = sync.bus().map(status -> status.connected()
                        ? language.text("admin.diagnostic.sync-bus-ok", "target", status.description())
                        : language.text("admin.diagnostic.sync-bus-ko", "target", status.description(),
                                "error", status.lastError().isEmpty() ? "?" : status.lastError()))
                .orElse("-");
        String poll = stats.lastPollError().map(error -> language.text("admin.diagnostic.sync-poll-ko", "error", error))
                .orElseGet(() -> stats.lastPoll().map(at -> language.text("admin.diagnostic.sync-poll-ok", "at", at))
                        .orElse(language.text("admin.diagnostic.sync-poll-none")));
        return language.text("admin.diagnostic.sync-value", "server", stats.serverId(), "bus", bus, "poll", poll,
                "published", stats.published(), "refused", stats.publishRefused(), "frombus", stats.fromBus(),
                "fromstorage", stats.fromStorage());
    }

    private static String io(Language language, IoStats io) {
        return language.text("admin.diagnostic.io-value", "threads", io.threads(), "max", io.maxThreads(),
                "active", io.active(), "queued", io.queued(), "capacity", io.queueCapacity(),
                "completed", io.completed(), "shutdown", io.shutdown() ? language.text("admin.diagnostic.io-shutdown") : "");
    }

    private static String line(Language language, String labelKey, String value) {
        return language.text("admin.diagnostic.line", "label", language.text("admin.diagnostic." + labelKey),
                "value", value);
    }
}
