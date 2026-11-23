package dev.novadev.novabank.libraries.download;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DownloadSettingsTest {

    @Test
    void par_defaut_central_puis_les_miroirs_avec_slash_final() {
        DownloadSettings settings = DownloadSettings.defaults(List.of(URI.create("https://mirror.example/maven2")));

        assertThat(settings.repositories()).containsExactly(DownloadSettings.MAVEN_CENTRAL,
                URI.create("https://mirror.example/maven2/"));
        assertThat(settings.attemptsPerRepository()).isEqualTo(3);
        assertThat(settings.connectTimeout()).isPositive();
        assertThat(settings.readTimeout()).isPositive();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ftp://mirror.example/", "file:///tmp/repo/", "https://user:pass@mirror.example/",
            "https://mirror.example/?x=1", "https://mirror.example/#frag", "mirror.example/maven2", "https:///path"})
    void refuse_un_depot_invalide(String uri) {
        assertThatThrownBy(() -> DownloadSettings.defaults(List.of(URI.create(uri))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refuse_des_reglages_hors_bornes() {
        List<URI> repos = List.of(DownloadSettings.MAVEN_CENTRAL);
        Duration second = Duration.ofSeconds(1);
        assertThatThrownBy(() -> new DownloadSettings(List.of(), second, second, 1, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DownloadSettings(repos, Duration.ZERO, second, 1, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DownloadSettings(repos, second, second.negated(), 1, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DownloadSettings(repos, second, second, 0, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DownloadSettings(repos, second, second, DownloadSettings.MAX_ATTEMPTS + 1,
                Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DownloadSettings(repos, second, second, 1, second.negated()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new DownloadSettings(repos, second, second, DownloadSettings.MAX_ATTEMPTS, Duration.ZERO)
                .attemptsPerRepository()).isEqualTo(DownloadSettings.MAX_ATTEMPTS);
    }
}
