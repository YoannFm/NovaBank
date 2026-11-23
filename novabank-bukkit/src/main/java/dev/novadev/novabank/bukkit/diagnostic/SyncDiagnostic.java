package dev.novadev.novabank.bukkit.diagnostic;

import dev.novadev.novabank.core.sync.SyncBusStatus;
import dev.novadev.novabank.core.sync.SyncStats;
import java.util.Objects;
import java.util.Optional;

/**
 * Etat de la synchronisation multi-serveur, sans secret.
 *
 * @param stats compteurs du coordinateur
 * @param bus   etat du bus Redis
 */
public record SyncDiagnostic(SyncStats stats, Optional<SyncBusStatus> bus) {

    /**
     * @throws NullPointerException si un champ est nul
     */
    public SyncDiagnostic {
        Objects.requireNonNull(stats, "stats");
        Objects.requireNonNull(bus, "bus");
    }
}
