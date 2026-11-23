package dev.novadev.novabank.config;

import static dev.novadev.novabank.config.TestConfigs.loadValid;
import static dev.novadev.novabank.config.TestConfigs.minimal;
import static dev.novadev.novabank.config.TestConfigs.minimalWith;
import static dev.novadev.novabank.config.TestConfigs.singleError;
import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.config.model.IslandLevelSettings;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IntegrationsSectionTest {

    @Test
    void section_absente_tout_est_desactive() {
        assertThat(loadValid(minimal()).integrations().superiorSkyblock2()).isEqualTo(IslandLevelSettings.disabled());
        assertThat(loadValid(minimal()).integrations().bentoBox().enabled()).isFalse();
    }

    @Test
    void bentobox_toujours_en_bonus_de_niveau() {
        IslandLevelSettings read = loadValid(minimalWith("integrations", Map.of("bentobox",
                Map.of("island-level", true, "multiplier", "0.5")))).integrations().bentoBox();

        assertThat(read.enabled()).isTrue();
        assertThat(read.target()).isEqualTo(IslandLevelSettings.Target.LEVEL);
        assertThat(read.multiplier()).isEqualByComparingTo(new BigDecimal("0.5"));
        singleError(minimalWith("integrations.bentobox.multiplier", "-1"), "integrations.bentobox.multiplier");
    }

    @Test
    void valeurs_valides_lues() {
        IslandLevelSettings read = loadValid(minimalWith("integrations", Map.of("superiorskyblock2",
                Map.of("island-level", true, "apply-to", "LEVEL", "multiplier", "0.01")))).integrations()
                .superiorSkyblock2();

        assertThat(read.enabled()).isTrue();
        assertThat(read.target()).isEqualTo(IslandLevelSettings.Target.LEVEL);
        assertThat(read.multiplier()).isEqualByComparingTo(new BigDecimal("0.01"));
    }

    @Test
    void coefficient_negatif_ou_illisible_et_bonus_inconnu_refuses() {
        singleError(minimalWith("integrations.superiorskyblock2.multiplier", "-1"),
                "integrations.superiorskyblock2.multiplier");
        singleError(minimalWith("integrations.superiorskyblock2.multiplier", "beaucoup"),
                "integrations.superiorskyblock2.multiplier");
        singleError(minimalWith("integrations.superiorskyblock2.apply-to", "xp"),
                "integrations.superiorskyblock2.apply-to");
    }
}
