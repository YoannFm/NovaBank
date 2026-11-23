package dev.novadev.novabank.core.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void les_montants_sont_ramenes_a_quatre_decimales_en_arrondi_bancaire() {
        assertThat(Money.of(new BigDecimal("1.00005")).toPlainString()).isEqualTo("1.0000");
        assertThat(Money.of(new BigDecimal("1.00015")).toPlainString()).isEqualTo("1.0002");
        assertThat(Money.ZERO.scale()).isEqualTo(Money.SCALE);
    }

    @Test
    void times_multiplie_puis_arrondit_une_seule_fois() {
        assertThat(Money.times(new BigDecimal("0.33333"), 3).toPlainString()).isEqualTo("1.0000");
        assertThat(Money.times(new BigDecimal("2.5"), Long.MAX_VALUE)).isEqualByComparingTo("23058430092136939517.5");
    }

    @Test
    void share_rend_la_totalite_sans_residu() {
        BigDecimal total = new BigDecimal("0.6667");

        assertThat(Money.share(total, 1, 2).toPlainString()).isEqualTo("0.3334");
        assertThat(Money.share(total, 2, 2)).isEqualTo(total);
        assertThat(Money.share(total, 0, 0)).isEqualTo(total);
        assertThat(Money.share(total, 0, 3)).isZero();
    }

    @Test
    void share_refuse_une_part_hors_bornes() {
        assertThatThrownBy(() -> Money.share(BigDecimal.ONE, 3, 2)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Money.share(BigDecimal.ONE, -1, 2)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void atLeastZero_borne_par_le_bas() {
        assertThat(Money.atLeastZero(new BigDecimal("-0.01"))).isEqualTo(Money.ZERO);
        assertThat(Money.atLeastZero(BigDecimal.TEN)).isEqualByComparingTo("10");
    }
}
