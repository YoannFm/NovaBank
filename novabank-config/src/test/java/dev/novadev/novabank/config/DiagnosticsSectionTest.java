package dev.novadev.novabank.config;

import static dev.novadev.novabank.config.TestConfigs.loadValid;
import static dev.novadev.novabank.config.TestConfigs.minimal;
import static dev.novadev.novabank.config.TestConfigs.minimalWith;
import static dev.novadev.novabank.config.TestConfigs.singleError;
import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.config.model.DiagnosticsSettings;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DiagnosticsSectionTest {

    @Test
    void valeurs_valides_lues() {
        assertThat(loadValid(minimalWith("diagnostics", Map.of("debug", true, "log-operations", "TRUE"))).diagnostics())
                .isEqualTo(new DiagnosticsSettings(true, true));
    }

    @Test
    void section_absente_valeurs_par_defaut() {
        assertThat(loadValid(minimal()).diagnostics()).isEqualTo(new DiagnosticsSettings(false, false));
    }

    @Test
    void booleen_invalide_refuse() {
        singleError(minimalWith("diagnostics.debug", "yes"), "diagnostics.debug");
    }
}
