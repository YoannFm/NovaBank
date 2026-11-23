package dev.novadev.novabank.bukkit.config;

import dev.novadev.novabank.config.validation.Severity;
import dev.novadev.novabank.config.validation.ValidationIssue;
import dev.novadev.novabank.config.validation.ValidationReport;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Journalise un rapport de validation : une ligne par probleme, avec son chemin. Les
 * messages de validation ne reprennent jamais la valeur d'un secret.
 */
public final class ValidationLog {

    private ValidationLog() {
    }

    /**
     * @param logger journal
     * @param report rapport
     */
    public static void log(Logger logger, ValidationReport report) {
        for (ValidationIssue issue : report.issues()) {
            logger.log(issue.severity() == Severity.ERROR ? Level.SEVERE : Level.WARNING, issue.toString());
        }
    }
}
