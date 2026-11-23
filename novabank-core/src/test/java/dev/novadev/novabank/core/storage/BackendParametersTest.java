package dev.novadev.novabank.core.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class BackendParametersTest {

    private static final String SECRET = "mongodb://u:Tres-Secret@h";

    private final BackendParameters parameters = BackendParameters.builder(Clock.systemUTC(),
                    Logger.getAnonymousLogger())
            .putSecret("uri", SECRET)
            .put("database", "novabank")
            .put("timeout", 2_000)
            .putSecret("bad-number", "Tres-Secret")
            .build();

    @Test
    void les_valeurs_sont_lues_par_cle() {
        assertThat(parameters.text("uri")).isEqualTo(SECRET);
        assertThat(parameters.text("database")).isEqualTo("novabank");
        assertThat(parameters.integer("timeout", 100, 5_000)).isEqualTo(2_000);
        assertThat(parameters.isSecret("uri")).isTrue();
        assertThat(parameters.isSecret("database")).isFalse();
        assertThat(parameters.keys()).containsExactly("uri", "database", "timeout", "bad-number");
    }

    @Test
    void absence_type_et_bornes_sont_verifies_sans_reveler_un_secret() {
        assertThatThrownBy(() -> parameters.text("absent")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("absent");
        assertThatThrownBy(() -> parameters.integer("timeout", 100, 1_000)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2000");
        assertThatThrownBy(() -> parameters.integer("bad-number", 0, 10)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("Tres-Secret").hasMessageContaining("****");
    }

    @Test
    void toString_masque_les_secrets() {
        assertThat(parameters.toString()).contains("database=novabank").contains("uri=****")
                .doesNotContain("Tres-Secret");
    }

    @Test
    void une_cle_redefinie_en_clair_n_est_plus_secrete() {
        BackendParameters redefined = BackendParameters.builder(Clock.systemUTC(), Logger.getAnonymousLogger())
                .putSecret("k", "a").put("k", "b").build();
        assertThat(redefined.isSecret("k")).isFalse();
    }
}
