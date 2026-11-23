package dev.novadev.novabank.config;

import dev.novadev.novabank.config.validation.ValidationReport;
import java.util.Objects;

/**
 * Resultat d'une modification en jeu.
 *
 * @param applied {@code true} si la modification a ete acceptee
 * @param state   nouvel etat si acceptee, sinon l'etat d'origine inchange
 * @param report  issues de la tentative (raison du refus, ou avertissements de la nouvelle configuration)
 */
public record EditOutcome(boolean applied, ConfigLoadResult state, ValidationReport report) {

    /**
     * Verifie la presence des champs.
     *
     * @throws NullPointerException si un champ est nul
     */
    public EditOutcome {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(report, "report");
    }
}
