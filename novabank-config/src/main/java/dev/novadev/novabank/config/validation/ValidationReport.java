package dev.novadev.novabank.config.validation;

import java.util.ArrayList;
import java.util.List;

/**
 * Ensemble immuable des issues relevees lors d'une validation, dans l'ordre de
 * detection. Une configuration invalide ne leve jamais d'exception : toutes les
 * issues sont accumulees ici.
 *
 * @param issues issues relevees
 */
public record ValidationReport(List<ValidationIssue> issues) {

    /**
     * Copie defensive de la liste.
     *
     * @throws NullPointerException si la liste ou un element est nul
     */
    public ValidationReport {
        issues = List.copyOf(issues);
    }

    /**
     * Rapport contenant une seule erreur.
     *
     * @param path    chemin concerne
     * @param message explication
     * @return le rapport
     */
    public static ValidationReport ofError(String path, String message) {
        return new ValidationReport(List.of(new ValidationIssue(Severity.ERROR, path, message)));
    }

    /**
     * Indique si au moins une issue est bloquante.
     *
     * @return {@code true} s'il existe une {@link Severity#ERROR}
     */
    public boolean hasErrors() {
        return issues.stream().anyMatch(issue -> issue.severity() == Severity.ERROR);
    }

    /**
     * Issues bloquantes.
     *
     * @return les erreurs, dans l'ordre de detection
     */
    public List<ValidationIssue> errors() {
        return bySeverity(Severity.ERROR);
    }

    /**
     * Issues non bloquantes.
     *
     * @return les avertissements, dans l'ordre de detection
     */
    public List<ValidationIssue> warnings() {
        return bySeverity(Severity.WARNING);
    }

    private List<ValidationIssue> bySeverity(Severity severity) {
        return issues.stream().filter(issue -> issue.severity() == severity).toList();
    }

    /**
     * Cree un constructeur de rapport vide.
     *
     * @return un nouveau constructeur
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Accumulateur d'issues, non thread-safe (une validation = un thread).
     */
    public static final class Builder {

        private final List<ValidationIssue> issues = new ArrayList<>();

        private Builder() {
        }

        /**
         * Ajoute une erreur bloquante.
         *
         * @param path    chemin concerne
         * @param message explication
         * @return ce constructeur
         */
        public Builder error(String path, String message) {
            issues.add(new ValidationIssue(Severity.ERROR, path, message));
            return this;
        }

        /**
         * Ajoute un avertissement.
         *
         * @param path    chemin concerne
         * @param message explication
         * @return ce constructeur
         */
        public Builder warning(String path, String message) {
            issues.add(new ValidationIssue(Severity.WARNING, path, message));
            return this;
        }

        /**
         * Ajoute toutes les issues d'un rapport existant.
         *
         * @param report rapport a recopier
         * @return ce constructeur
         */
        public Builder addAll(ValidationReport report) {
            issues.addAll(report.issues());
            return this;
        }

        /**
         * Fige les issues accumulees.
         *
         * @return le rapport
         */
        public ValidationReport build() {
            return new ValidationReport(issues);
        }
    }
}
