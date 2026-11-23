package dev.novadev.novabank.bukkit.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class TicksTest {

    @Test
    void conversion_arrondie_au_tick_superieur() {
        assertThat(Ticks.ceil(Duration.ZERO)).isZero();
        assertThat(Ticks.ceil(Duration.ofMillis(1))).isEqualTo(1);
        assertThat(Ticks.ceil(Duration.ofMillis(50))).isEqualTo(1);
        assertThat(Ticks.ceil(Duration.ofMillis(51))).isEqualTo(2);
        assertThat(Ticks.ceil(Duration.ofSeconds(1))).isEqualTo(20);
        assertThat(Ticks.ceil(Duration.ofHours(6))).isEqualTo(432_000);
    }

    @Test
    void une_periode_fait_au_moins_un_tick() {
        assertThat(Ticks.period(Duration.ofMillis(1))).isEqualTo(1);
        assertThat(Ticks.period(Duration.ofMinutes(1))).isEqualTo(1_200);
    }

    @Test
    void durees_invalides_refusees() {
        assertThatThrownBy(() -> Ticks.ceil(Duration.ofMillis(-1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Ticks.period(Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }
}
