package dev.novadev.novabank.bukkit.message;

import dev.novadev.novabank.config.validation.Severity;
import dev.novadev.novabank.config.validation.ValidationIssue;

/**
 * Ligne affichable d'un probleme de configuration (rouge pour une erreur, jaune pour un
 * avertissement), sans valeur secrete.
 */
public final class ValidationLines {

    private ValidationLines() {
    }

    /**
     * @param issue probleme de configuration
     * @return la ligne coloree
     */
    public static String of(ValidationIssue issue) {
        return ColorCodes.translate(issue.severity() == Severity.ERROR ? "&c" : "&e") + issue;
    }
}
