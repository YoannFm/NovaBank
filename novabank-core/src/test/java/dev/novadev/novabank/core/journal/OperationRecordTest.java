package dev.novadev.novabank.core.journal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.api.OperationId;
import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.api.operation.OperationType;
import dev.novadev.novabank.core.testing.TestBankIds;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OperationRecordTest {

    private static final OperationId ID = OperationId.random();
    private static final BankId BANK = TestBankIds.random();
    private static final ItemKey STONE = ItemKey.minecraft("stone");
    private static final Instant AT = Instant.EPOCH;

    @Test
    void un_retrait_reserve_se_solde_une_seule_fois() {
        OperationRecord reserved = OperationRecord.reservation(ID, BANK, STONE, 5, 2, new BigDecimal("-2"), AT,
                UUID.randomUUID(), 0L);

        OperationRecord committed = reserved.settle(OperationState.COMMITTED, 3, 1, BigDecimal.ZERO);

        assertThat(committed.undeliveredQuantity()).isEqualTo(1L);
        assertThat(reserved.undeliveredQuantity()).isZero();
        assertThatThrownBy(() -> committed.settle(OperationState.RETURNED, 0, 5, BigDecimal.ZERO))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void les_entrees_incoherentes_sont_refusees() {
        assertThatThrownBy(() -> new OperationRecord(ID, OperationType.RESET, BANK, STONE, 1, 0, BigDecimal.ZERO,
                OperationState.COMMITTED, AT, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OperationRecord(ID, OperationType.DEPOSIT, BANK, STONE, 1, 2, BigDecimal.ZERO,
                OperationState.COMMITTED, AT, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OperationRecord(ID, OperationType.DEPOSIT, BANK, STONE, 1, 1, BigDecimal.ZERO,
                OperationState.RESERVED, AT, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OperationRecord(ID, OperationType.WITHDRAW, BANK, STONE, 1, 1, BigDecimal.ZERO,
                OperationState.RESERVED, AT, null)).isInstanceOf(IllegalArgumentException.class);
        UUID player = UUID.randomUUID();
        assertThatThrownBy(() -> new OperationRecord(ID, OperationType.WITHDRAW, BANK, STONE, 4, 0, BigDecimal.ZERO,
                OperationState.RETURNED, AT, new WithdrawalProgress(player, 0, 0, 3, BigDecimal.ZERO)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OperationRecord(ID, OperationType.WITHDRAW, BANK, STONE, 4, 0, BigDecimal.ZERO,
                OperationState.RESERVED, AT, new WithdrawalProgress(player, 0, 1, 0, BigDecimal.ZERO)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OperationRecord(ID, OperationType.WITHDRAW, BANK, STONE, 4, 0, BigDecimal.ZERO,
                OperationState.COMMITTED, AT, new WithdrawalProgress(player, 0, 3, 2, BigDecimal.ZERO)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WithdrawalProgress(player, 0, -1, 0, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WithdrawalProgress(player, -1, 0, 0, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
