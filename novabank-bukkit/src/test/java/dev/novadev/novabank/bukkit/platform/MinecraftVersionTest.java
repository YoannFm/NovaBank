package dev.novadev.novabank.bukkit.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class MinecraftVersionTest {

    @ParameterizedTest
    @CsvSource({
            "1.21.11-R0.1-SNAPSHOT, 1, 21, 11",
            "1.17.1-R0.1-SNAPSHOT, 1, 17, 1",
            "1.17-R0.1-SNAPSHOT, 1, 17, 0",
            "26.3.build.226-stable, 26, 3, 0",
            "26.3-R0.1-SNAPSHOT, 26, 3, 0",
            "26.1.2.build.40-stable, 26, 1, 2",
            "26.3, 26, 3, 0",
            "' 1.20.6-R0.1-SNAPSHOT ', 1, 20, 6"
    })
    void lit_les_formats_ancien_et_nouveau(String raw, int major, int minor, int patch) {
        assertThat(MinecraftVersion.parse(raw)).contains(new MinecraftVersion(major, minor, patch));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"R0.1-SNAPSHOT", "version", "1", "1-R0.1", "a.b.c"})
    void un_format_inconnu_donne_une_version_vide(String raw) {
        assertThat(MinecraftVersion.parse(raw)).isEmpty();
    }

    @Test
    void les_versions_se_comparent_numeriquement() {
        MinecraftVersion v1_17 = parsed("1.17-R0.1-SNAPSHOT");
        MinecraftVersion v1_17_1 = parsed("1.17.1-R0.1-SNAPSHOT");
        MinecraftVersion v1_21_11 = parsed("1.21.11-R0.1-SNAPSHOT");
        MinecraftVersion v1_21_9 = parsed("1.21.9-R0.1-SNAPSHOT");
        MinecraftVersion v26_3 = parsed("26.3.build.226-stable");

        assertThat(v1_17).isEqualByComparingTo(MinecraftVersion.MINIMUM);
        assertThat(v1_17_1).isGreaterThan(v1_17);
        assertThat(v1_21_9).isLessThan(v1_21_11);
        assertThat(v26_3).isGreaterThan(v1_21_11);
        assertThat(parsed("1.16.5-R0.1-SNAPSHOT").isOlderThan(MinecraftVersion.MINIMUM)).isTrue();
        assertThat(v1_17.isOlderThan(MinecraftVersion.MINIMUM)).isFalse();
    }

    @Test
    void l_affichage_omet_le_correctif_nul() {
        assertThat(new MinecraftVersion(26, 3, 0)).hasToString("26.3");
        assertThat(new MinecraftVersion(1, 21, 11)).hasToString("1.21.11");
    }

    @Test
    void une_composante_negative_est_refusee() {
        assertThatThrownBy(() -> new MinecraftVersion(1, -1, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    private static MinecraftVersion parsed(String raw) {
        Optional<MinecraftVersion> version = MinecraftVersion.parse(raw);
        assertThat(version).isPresent();
        return version.get();
    }
}
