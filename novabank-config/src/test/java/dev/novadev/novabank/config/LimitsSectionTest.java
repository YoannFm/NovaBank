package dev.novadev.novabank.config;

import static dev.novadev.novabank.config.TestConfigs.loadValid;
import static dev.novadev.novabank.config.TestConfigs.minimal;
import static dev.novadev.novabank.config.TestConfigs.minimalWith;
import static dev.novadev.novabank.config.TestConfigs.singleError;
import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.config.model.LimitSettings;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LimitsSectionTest {

    @Test
    void valeurs_valides_lues() {
        LimitSettings limits = loadValid(minimalWith("limits",
                Map.of("max-quantity-per-operation", "64", "cooldown-ms", 0))).limits();
        assertThat(limits).isEqualTo(new LimitSettings(64, 0));
    }

    @Test
    void section_absente_valeurs_par_defaut() {
        assertThat(loadValid(minimal()).limits()).isEqualTo(new LimitSettings(2304, 500));
    }

    @Test
    void valeurs_invalides_refusees() {
        singleError(minimalWith("limits.max-quantity-per-operation", 0), "limits.max-quantity-per-operation");
        singleError(minimalWith("limits.max-quantity-per-operation", 2.5), "limits.max-quantity-per-operation");
        singleError(minimalWith("limits.cooldown-ms", -1), "limits.cooldown-ms");
    }

    @Test
    void decimal_sans_partie_fractionnaire_accepte() {
        assertThat(loadValid(minimalWith("limits.cooldown-ms", 250.0)).limits().cooldownMs()).isEqualTo(250);
    }
}
