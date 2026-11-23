package dev.novadev.novabank.api.operation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.novadev.novabank.api.OperationId;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class OperationResultTest {

    private static final OperationId ID = OperationId.random();

    @Test
    void un_refus_n_a_aucun_effet() {
        OperationResult rejected = OperationResult.rejected(ID, OperationStatus.INSUFFICIENT_STOCK);

        assertThat(rejected.isSuccess()).isFalse();
        assertThat(rejected.isEffective()).isFalse();
        assertThat(rejected.quantity()).isZero();
        assertThat(rejected.valueDelta()).isZero();
        assertThat(rejected.bankValue()).isEmpty();
        assertThat(rejected.operationId()).isEqualTo(ID);
    }

    @Test
    void un_rejeu_est_effectif_sans_etre_un_succes() {
        OperationResult duplicate = OperationResult.duplicate(ID, 3, BigDecimal.ONE, BigDecimal.TEN);

        assertThat(duplicate.isSuccess()).isFalse();
        assertThat(duplicate.isEffective()).isTrue();
        assertThat(duplicate.bankValue()).contains(BigDecimal.TEN);
    }

    @Test
    void rejected_exige_un_statut_de_refus() {
        assertThatThrownBy(() -> OperationResult.rejected(ID, OperationStatus.SUCCESS))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OperationResult.rejected(ID, OperationStatus.DUPLICATE_OPERATION))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void l_egalite_ignore_l_echelle_des_montants() {
        OperationResult a = OperationResult.success(ID, 2, new BigDecimal("5"), new BigDecimal("7.5"));
        OperationResult b = OperationResult.success(ID, 2, new BigDecimal("5.0000"), new BigDecimal("7.50"));

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(OperationResult.rejected(ID, OperationStatus.UNKNOWN_BANK));
        assertThat(a.toString()).contains("SUCCESS", "7.5");
    }
}
