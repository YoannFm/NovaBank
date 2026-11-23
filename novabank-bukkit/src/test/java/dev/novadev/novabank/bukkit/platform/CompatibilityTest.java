package dev.novadev.novabank.bukkit.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CompatibilityTest {

    @Test
    void les_versions_supportees_demarrent_sur_paper_et_spigot() {
        assertThat(refusal("1.17.1-R0.1-SNAPSHOT", PlatformDetector.LEGACY_PAPER_MARKER))
                .isEmpty();
        assertThat(refusal("1.21.11-R0.1-SNAPSHOT", PlatformDetector.PAPER_MARKER))
                .isEmpty();
        assertThat(refusal("26.3.build.226-stable", PlatformDetector.SPIGOT_MARKER))
                .isEmpty();
    }

    @Test
    void une_version_anterieure_a_1_17_est_refusee_avec_un_message_clair() {
        assertThat(refusal("1.16.5-R0.1-SNAPSHOT", PlatformDetector.LEGACY_PAPER_MARKER))
                .hasValueSatisfying(reason -> assertThat(reason).contains("1.16.5", "1.17", "ne demarre pas"));
    }

    @Test
    void folia_demarre_a_partir_de_la_version_minimale() {
        assertThat(refusal("1.19.4-R0.1-SNAPSHOT", PlatformDetector.FOLIA_MARKER)).isEmpty();
        assertThat(refusal("1.21.11-R0.1-SNAPSHOT", PlatformDetector.FOLIA_MARKER)).isEmpty();
        assertThat(refusal("26.1.2.build.8-stable", PlatformDetector.FOLIA_MARKER)).isEmpty();
        assertThat(refusal("26.3.build.5-alpha", PlatformDetector.FOLIA_MARKER)).isEmpty();
    }

    @Test
    void folia_anterieur_au_minimum_est_refuse_meme_si_paper_l_accepterait() {
        assertThat(refusal("1.19.3-R0.1-SNAPSHOT", PlatformDetector.FOLIA_MARKER))
                .hasValueSatisfying(reason -> assertThat(reason).contains("Folia 1.19.3", "Folia 1.19.4",
                        "ne demarre pas"));
        assertThat(refusal("1.19.3-R0.1-SNAPSHOT", PlatformDetector.PAPER_MARKER)).isEmpty();
    }

    @Test
    void une_version_illisible_demarre_avec_un_avertissement() {
        ServerEnvironment environment = environment("inconnue", PlatformDetector.PAPER_MARKER);

        assertThat(Compatibility.refusalReason(environment)).isEmpty();
        assertThat(Compatibility.warning(environment)).hasValueSatisfying(
                warning -> assertThat(warning).contains("inconnue", "non verifiee"));
        assertThat(environment.versionLabel()).isEqualTo("inconnue");
        assertThat(warning("1.21.11-R0.1-SNAPSHOT", PlatformDetector.PAPER_MARKER)).isEmpty();
    }

    private static Optional<String> refusal(String bukkitVersion, String marker) {
        return Compatibility.refusalReason(environment(bukkitVersion, marker));
    }

    private static Optional<String> warning(String bukkitVersion, String marker) {
        return Compatibility.warning(environment(bukkitVersion, marker));
    }

    private static ServerEnvironment environment(String bukkitVersion, String marker) {
        return ServerEnvironment.detect(bukkitVersion, "test", Set.of(marker)::contains);
    }
}
