package dev.novadev.novabank.core.bank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class StockLineTest {

    @Test
    void les_unites_comptees_sont_au_dessus_de_la_base() {
        assertThat(new StockLine(10, 4, BigDecimal.ZERO).countedQuantity()).isEqualTo(6L);
        assertThat(new StockLine(3, 4, BigDecimal.ZERO).countedQuantity()).isZero();
    }

    @Test
    void retirer_ne_baisse_jamais_la_base() {
        StockLine line = new StockLine(10, 8, BigDecimal.ZERO).minus(10);

        assertThat(line.baselineQuantity()).isEqualTo(8L);
        assertThat(line.isBlank()).isFalse();
        assertThat(line.plus(8).countedQuantity()).isZero();
        assertThat(line.plus(9).countedQuantity()).isEqualTo(1L);
    }

    @Test
    void les_unites_non_comptees_comblent_le_deficit_puis_montent_la_base() {
        StockLine underBase = new StockLine(2, 5, BigDecimal.ZERO);
        assertThat(underBase.plusUncounted(2)).isEqualTo(new StockLine(4, 5, BigDecimal.ZERO));
        assertThat(underBase.plusUncounted(6)).isEqualTo(new StockLine(8, 8, BigDecimal.ZERO));

        StockLine aboveBase = new StockLine(9, 5, BigDecimal.ONE);
        assertThat(aboveBase.plusUncounted(3)).isEqualTo(new StockLine(12, 8, BigDecimal.ONE));
    }

    @Test
    void reset_fige_tout_le_stock_dans_la_base() {
        assertThat(new StockLine(7, 2, BigDecimal.TEN).reset()).isEqualTo(new StockLine(7, 7, BigDecimal.ZERO));
    }

    @Test
    void les_invariants_sont_verifies() {
        assertThatThrownBy(() -> new StockLine(-1, 0, BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StockLine(0, -1, BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StockLine(0, 0, BigDecimal.ONE.negate()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StockLine.EMPTY.minus(1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StockLine.EMPTY.plus(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StockLine(Long.MAX_VALUE, 0, BigDecimal.ZERO).plus(1))
                .isInstanceOf(ArithmeticException.class);
    }
}
