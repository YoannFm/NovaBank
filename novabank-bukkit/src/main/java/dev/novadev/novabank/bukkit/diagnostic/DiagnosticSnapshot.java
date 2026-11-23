package dev.novadev.novabank.bukkit.diagnostic;

import dev.novadev.novabank.bukkit.platform.ServerEnvironment;
import dev.novadev.novabank.bukkit.scheduler.IoStats;
import dev.novadev.novabank.config.model.StorageType;
import dev.novadev.novabank.config.validation.ValidationIssue;
import dev.novadev.novabank.core.ranking.LeaderboardStats;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Etat du plugin a un instant donne, sans aucune donnee secrete.
 *
 * @param pluginVersion  version de NovaBank
 * @param environment    environnement du serveur
 * @param storageType    backend en service
 * @param database       resultat de la requete de verification
 * @param driverId       identifiant du pilote charge
 * @param driverArtifact version de l'artefact du pilote
 * @param driverDetail   precision sur le chargement du pilote (version JDBC annoncee, module embarque)
 * @param acceptedItems  nombre d'items acceptes en vigueur
 * @param warnings       avertissements de la configuration en vigueur
 * @param io             etat de l'executeur IO
 * @param sync           synchronisation multi-serveur, vide si desactivee
 * @param leaderboards   etat des classements, vide si aucun type n'est classe
 * @param recentErrors   derniers avertissements et erreurs du journal, le plus ancien en premier
 * @param components     integrations et fournisseurs d'items charges, avec la version du plugin tiers
 */
public record DiagnosticSnapshot(String pluginVersion, ServerEnvironment environment, StorageType storageType,
                                 DatabaseStatus database, String driverId, String driverArtifact,
                                 String driverDetail, int acceptedItems, List<ValidationIssue> warnings,
                                 IoStats io, Optional<SyncDiagnostic> sync,
                                 Optional<LeaderboardStats> leaderboards, List<String> recentErrors,
                                 List<String> components) {

    /**
     * @throws NullPointerException si un champ est nul
     */
    public DiagnosticSnapshot {
        Objects.requireNonNull(pluginVersion, "pluginVersion");
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(storageType, "storageType");
        Objects.requireNonNull(database, "database");
        Objects.requireNonNull(driverId, "driverId");
        Objects.requireNonNull(driverArtifact, "driverArtifact");
        Objects.requireNonNull(driverDetail, "driverDetail");
        warnings = List.copyOf(warnings);
        recentErrors = List.copyOf(recentErrors);
        components = List.copyOf(components);
        Objects.requireNonNull(io, "io");
        Objects.requireNonNull(sync, "sync");
        Objects.requireNonNull(leaderboards, "leaderboards");
    }
}
