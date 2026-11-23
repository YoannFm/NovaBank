package dev.novadev.novabank.bukkit.config;

import dev.novadev.novabank.config.validation.ValidationReport;
import java.util.Objects;

/**
 * Resultat d'un rechargement de la configuration.
 *
 * @param applied         {@code true} si la nouvelle configuration est en vigueur
 * @param report          rapport de validation de la nouvelle configuration
 * @param restartRequired {@code true} si une partie appliquee seulement au redemarrage a change (stockage,
 *                        commandes, politique des items non remis)
 * @param acceptedItems   nombre d'items acceptes en vigueur apres le rechargement
 */
public record ReloadOutcome(boolean applied, ValidationReport report, boolean restartRequired, int acceptedItems) {

    /**
     * @throws NullPointerException si le rapport est nul
     */
    public ReloadOutcome {
        Objects.requireNonNull(report, "report");
    }
}
