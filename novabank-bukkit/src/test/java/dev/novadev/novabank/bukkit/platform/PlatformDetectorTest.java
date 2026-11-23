package dev.novadev.novabank.bukkit.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class PlatformDetectorTest {

    @Test
    void folia_est_prioritaire_sur_paper() {
        assertThat(detect(PlatformDetector.FOLIA_MARKER, PlatformDetector.PAPER_MARKER, PlatformDetector.SPIGOT_MARKER))
                .isEqualTo(ServerPlatform.FOLIA);
    }

    @Test
    void paper_recent_et_ancien_sont_reconnus() {
        assertThat(detect(PlatformDetector.PAPER_MARKER, PlatformDetector.SPIGOT_MARKER))
                .isEqualTo(ServerPlatform.PAPER);
        assertThat(detect(PlatformDetector.LEGACY_PAPER_MARKER, PlatformDetector.SPIGOT_MARKER))
                .isEqualTo(ServerPlatform.PAPER);
    }

    @Test
    void spigot_puis_bukkit_par_defaut() {
        assertThat(detect(PlatformDetector.SPIGOT_MARKER)).isEqualTo(ServerPlatform.SPIGOT);
        assertThat(detect()).isEqualTo(ServerPlatform.BUKKIT);
    }

    @Test
    void la_sonde_par_chargeur_ne_trouve_que_les_classes_visibles() {
        ClassPresence presence = ClassPresence.of(getClass().getClassLoader());

        assertThat(presence.isPresent("org.bukkit.Bukkit")).isTrue();
        assertThat(presence.isPresent(PlatformDetector.FOLIA_MARKER)).isFalse();
        assertThat(PlatformDetector.detect(presence)).isEqualTo(ServerPlatform.BUKKIT);
    }

    private static ServerPlatform detect(String... presentClasses) {
        Set<String> present = Set.of(presentClasses);
        return PlatformDetector.detect(present::contains);
    }
}
