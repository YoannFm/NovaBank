package dev.novadev.novabank.config;

import dev.novadev.novabank.config.validation.ValidationReport;
import dev.novadev.novabank.core.text.ConsoleText;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Resultat d'un chargement de configuration.
 *
 * @param config   configuration, presente si et seulement si le rapport ne contient aucune erreur
 * @param report   issues relevees
 * @param tree     arbre lu, apres migration de schema, non modifiable ; base des modifications en jeu
 * @param migrated {@code true} si une migration de schema a ete appliquee (la plateforme
 *                 peut alors reecrire le fichier)
 */
public record ConfigLoadResult(Optional<NovaBankConfig> config, ValidationReport report, Map<String, Object> tree,
                               boolean migrated) {

    /**
     * Verifie la coherence configuration / rapport.
     *
     * @throws NullPointerException     si un champ est nul
     * @throws IllegalArgumentException si la presence de la configuration contredit le rapport
     */
    public ConfigLoadResult {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(tree, "tree");
        if (config.isPresent() == report.hasErrors()) {
            throw new IllegalArgumentException(
                    ConsoleText.tr("Configuration presente si et seulement si aucune erreur"));
        }
    }
}
