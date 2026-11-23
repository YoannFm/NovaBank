package dev.novadev.novabank.bukkit.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class BentoBoxLevelsTest {

    @Test
    void bonusIsBankValueTimesMultiplierRoundedDown() {
        assertThat(BentoBoxLevels.bonus(new BigDecimal("2.5"), new BigDecimal("20"))).isEqualTo(50);
        assertThat(BentoBoxLevels.bonus(new BigDecimal("2.5"), new BigDecimal("0.5"))).isEqualTo(1);
        assertThat(BentoBoxLevels.bonus(BigDecimal.ZERO, BigDecimal.ONE)).isZero();
    }
}
