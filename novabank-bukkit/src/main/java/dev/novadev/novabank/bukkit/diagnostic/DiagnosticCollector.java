package dev.novadev.novabank.bukkit.diagnostic;

import dev.novadev.novabank.bukkit.config.ActiveConfiguration;
import dev.novadev.novabank.bukkit.platform.ServerEnvironment;
import dev.novadev.novabank.bukkit.scheduler.IoExecutor;
import dev.novadev.novabank.bukkit.storage.ActiveStorage;
import dev.novadev.novabank.bukkit.storage.StorageDriver;
import dev.novadev.novabank.core.ranking.LeaderboardService;
import dev.novadev.novabank.core.sync.SyncCoordinator;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Collecte l'etat du plugin. Interroge la base : a appeler sur l'executeur IO.
 */
public final class DiagnosticCollector implements Supplier<DiagnosticSnapshot> {

    private final String pluginVersion;
    private final ServerEnvironment environment;
    private final ActiveConfiguration configuration;
    private final ActiveStorage storage;
    private final IoExecutor io;
    private final Optional<SyncCoordinator> sync;
    private final LeaderboardService leaderboards;
    private final RecentErrors recentErrors;
    private final java.util.List<String> components;

    /**
     * @param pluginVersion version de NovaBank
     * @param environment   environnement du serveur
     * @param configuration configuration en vigueur
     * @param storage       stockage ouvert
     * @param io            executeur IO
     * @param sync          coordination multi-serveur, vide si desactivee
     * @param leaderboards  classements
     * @param recentErrors  derniers avertissements et erreurs
     * @param components    integrations et fournisseurs d'items charges (liste vivante, remplie a l'assemblage)
     */
    public DiagnosticCollector(String pluginVersion, ServerEnvironment environment,
                               ActiveConfiguration configuration, ActiveStorage storage, IoExecutor io,
                               Optional<SyncCoordinator> sync, LeaderboardService leaderboards,
                               RecentErrors recentErrors, java.util.List<String> components) {
        this.pluginVersion = Objects.requireNonNull(pluginVersion, "pluginVersion");
        this.environment = Objects.requireNonNull(environment, "environment");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.storage = Objects.requireNonNull(storage, "storage");
        this.io = Objects.requireNonNull(io, "io");
        this.sync = Objects.requireNonNull(sync, "sync");
        this.leaderboards = Objects.requireNonNull(leaderboards, "leaderboards");
        this.recentErrors = Objects.requireNonNull(recentErrors, "recentErrors");
        this.components = Objects.requireNonNull(components, "components");
    }

    @Override
    public DiagnosticSnapshot get() {
        StorageDriver driver = storage.driver();
        return new DiagnosticSnapshot(pluginVersion, environment, storage.type(), checkDatabase(),
                driver.id(), driver.artifact(), driver.detail(),
                configuration.config().items().size(), configuration.report().warnings(), io.stats(),
                sync.map(coordinator -> new SyncDiagnostic(coordinator.stats(), coordinator.busStatus())),
                configuration.rankedTypes().isEmpty() ? Optional.empty() : Optional.of(leaderboards.stats()),
                recentErrors.snapshot(), components);
    }

    private DatabaseStatus checkDatabase() {
        long start = System.nanoTime();
        try {
            int version = storage.backend().checkSchemaVersion();
            return DatabaseStatus.reachable(version, elapsedMillis(start));
        } catch (RuntimeException failure) {
            return DatabaseStatus.unreachable(String.valueOf(failure.getMessage()), elapsedMillis(start));
        }
    }

    private static long elapsedMillis(long start) {
        return (System.nanoTime() - start) / 1_000_000L;
    }
}
