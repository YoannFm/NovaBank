package dev.novadev.novabank.core.migration;

import java.time.Duration;
import java.util.Objects;

/**
 * Bilan d'une migration reussie et verifiee.
 *
 * @param copied   resume du contenu copie (identique a la source et a la cible)
 * @param duration duree totale, copie et verification comprises
 */
public record MigrationReport(ContentSummary copied, Duration duration) {

    /** Construit un bilan. */
    public MigrationReport {
        Objects.requireNonNull(copied, "copied");
        Objects.requireNonNull(duration, "duration");
    }

    /**
     * @return une ligne de bilan, en francais
     */
    public String describe() {
        return copied.banks() + " banque(s), " + copied.stockLines() + " ligne(s) de stock, " + copied.operations()
                + " operation(s), " + copied.deliveries() + " livraison(s), " + copied.activeBanks()
                + " banque(s) active(s) copiees et verifiees en " + duration.toMillis() + " ms";
    }
}
