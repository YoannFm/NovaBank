package dev.novadev.novabank.config.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ValidationReportTest {

    @Test
    void erreurs_et_avertissements_separes_dans_l_ordre() {
        ValidationReport other = ValidationReport.builder().warning("c", "w2").build();
        ValidationReport report = ValidationReport.builder().warning("a", "w1").error("b", "e1").addAll(other).build();
        assertThat(report.hasErrors()).isTrue();
        assertThat(report.errors()).extracting(ValidationIssue::path).containsExactly("b");
        assertThat(report.warnings()).extracting(ValidationIssue::path).containsExactly("a", "c");
        assertThat(ValidationReport.builder().warning("a", "w").build().hasErrors()).isFalse();
    }

    @Test
    void format_lisible() {
        assertThat(ValidationReport.ofError("storage.sql.port", "hors bornes").issues().get(0))
                .hasToString("[ERREUR] storage.sql.port : hors bornes");
        assertThat(new ValidationIssue(Severity.WARNING, "a", "b")).hasToString("[AVERTISSEMENT] a : b");
    }

    @Test
    void champs_obligatoires() {
        assertThatThrownBy(() -> new ValidationIssue(null, "a", "b")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ValidationIssue(Severity.ERROR, null, "b")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ValidationIssue(Severity.ERROR, "a", null)).isInstanceOf(NullPointerException.class);
    }
}
