package dev.novadev.novabank.config;

import static dev.novadev.novabank.config.TestConfigs.load;
import static dev.novadev.novabank.config.TestConfigs.loadValid;
import static dev.novadev.novabank.config.TestConfigs.minimal;
import static dev.novadev.novabank.config.TestConfigs.minimalWith;
import static dev.novadev.novabank.config.TestConfigs.singleError;
import static dev.novadev.novabank.config.TestConfigs.warningPaths;
import static dev.novadev.novabank.config.TestConfigs.with;
import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.config.model.LeaderboardSettings;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LeaderboardsSectionTest {

    private static final LeaderboardSettings DEFAULTS = new LeaderboardSettings(true, 10, 60, false);

    @Test
    void valeurs_valides_lues_et_defaut_pour_type_sans_entree() {
        Map<String, Object> tree = with(minimalWith("banks.entity-types", List.of("player", "island")),
                "leaderboards.island", Map.of("enabled", false, "size", 1000, "refresh-seconds", 5, "per-category", true));
        Map<String, LeaderboardSettings> leaderboards = loadValid(tree).leaderboards();
        assertThat(leaderboards).containsOnlyKeys("player", "island");
        assertThat(leaderboards.get("island")).isEqualTo(new LeaderboardSettings(false, 1000, 5, true));
        assertThat(leaderboards.get("player")).isEqualTo(DEFAULTS);
    }

    @Test
    void section_absente_valeurs_par_defaut() {
        assertThat(loadValid(minimal()).leaderboards()).containsExactly(Map.entry("player", DEFAULTS));
    }

    @Test
    void type_non_active_ignore_avec_un_seul_avertissement() {
        ConfigLoadResult result = load(minimalWith("leaderboards.faction", Map.of("size", 5, "inconnu", 1)));
        assertThat(result.config().orElseThrow().leaderboards()).containsOnlyKeys("player");
        assertThat(warningPaths(result)).containsExactly("leaderboards.faction");
    }

    @Test
    void valeurs_invalides_refusees() {
        singleError(minimalWith("leaderboards.player.size", 0), "leaderboards.player.size");
        singleError(minimalWith("leaderboards.player.size", 1001), "leaderboards.player.size");
        singleError(minimalWith("leaderboards.player.refresh-seconds", 1), "leaderboards.player.refresh-seconds");
        singleError(minimalWith("leaderboards.player.per-category", 3), "leaderboards.player.per-category");
    }
}
