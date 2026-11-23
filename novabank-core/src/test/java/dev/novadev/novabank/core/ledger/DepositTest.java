package dev.novadev.novabank.core.ledger;

import static dev.novadev.novabank.core.ledger.LedgerFixture.DIAMOND;
import static dev.novadev.novabank.core.ledger.LedgerFixture.STONE;
import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.api.OperationId;
import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.api.operation.OperationResult;
import dev.novadev.novabank.api.operation.OperationStatus;
import dev.novadev.novabank.api.operation.OperationType;
import dev.novadev.novabank.api.valuation.ValuationMode;
import dev.novadev.novabank.core.journal.OperationRecord;
import dev.novadev.novabank.core.journal.OperationState;
import dev.novadev.novabank.core.testing.TestBankIds;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class DepositTest {

    @ParameterizedTest
    @EnumSource(ValuationMode.class)
    void un_depot_credite_le_stock_la_valeur_et_le_journal(ValuationMode mode) {
        LedgerFixture f = LedgerFixture.of(mode);
        OperationId id = OperationId.random();

        OperationResult result = f.ledger.deposit(id, f.bankId, DIAMOND, 4);

        assertThat(result.status()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(result.quantity()).isEqualTo(4L);
        assertThat(result.valueDelta()).isEqualByComparingTo("10");
        assertThat(result.bankValue()).hasValueSatisfying(value -> assertThat(value).isEqualByComparingTo("10"));
        assertThat(f.line(DIAMOND).quantity()).isEqualTo(4L);
        assertThat(f.bank().version()).isEqualTo(1L);
        OperationRecord record = f.record(id);
        assertThat(record.type()).isEqualTo(OperationType.DEPOSIT);
        assertThat(record.state()).isEqualTo(OperationState.COMMITTED);
        assertThat(record.countedQuantity()).isEqualTo(4L);
        assertThat(record.valueDelta()).isEqualByComparingTo("10");
        assertThat(record.timestamp()).isEqualTo(LedgerFixture.NOW);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L, Long.MIN_VALUE})
    void une_quantite_nulle_ou_negative_est_refusee(long quantity) {
        LedgerFixture f = LedgerFixture.of(ValuationMode.DERIVED);

        OperationResult result = f.deposit(STONE, quantity);

        assertThat(result.status()).isEqualTo(OperationStatus.INVALID_QUANTITY);
        assertThat(result.isEffective()).isFalse();
        assertThat(result.bankValue()).isEmpty();
        assertThat(f.repository.journal()).isEmpty();
        assertThat(f.bank().version()).isZero();
    }

    @ParameterizedTest
    @EnumSource(ValuationMode.class)
    void un_item_hors_catalogue_est_refuse(ValuationMode mode) {
        LedgerFixture f = LedgerFixture.of(mode);

        OperationResult result = f.deposit(ItemKey.minecraft("dirt"), 3);

        assertThat(result.status()).isEqualTo(OperationStatus.NOT_ACCEPTED);
        assertThat(f.bank().lines()).isEmpty();
        assertThat(f.repository.journal()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(ValuationMode.class)
    void un_debordement_de_stock_est_refuse_proprement(ValuationMode mode) {
        LedgerFixture f = LedgerFixture.of(mode);
        assertThat(f.deposit(STONE, Long.MAX_VALUE).isSuccess()).isTrue();

        OperationResult overflow = f.deposit(STONE, 1);

        assertThat(overflow.status()).isEqualTo(OperationStatus.QUANTITY_OVERFLOW);
        assertThat(f.line(STONE).quantity()).isEqualTo(Long.MAX_VALUE);
        assertThat(f.repository.journal()).hasSize(1);
        assertThat(f.value()).isEqualByComparingTo(String.valueOf(Long.MAX_VALUE));
    }

    @ParameterizedTest
    @EnumSource(ValuationMode.class)
    void plusieurs_items_sont_valorises_separement(ValuationMode mode) {
        LedgerFixture f = LedgerFixture.of(mode);

        f.deposit(STONE, 3);
        f.deposit(DIAMOND, 2);
        f.deposit(LedgerFixture.RUBY, 1);

        assertThat(f.value()).isEqualByComparingTo("18");
        assertThat(f.bank().lines()).hasSize(3);
    }

    @ParameterizedTest
    @EnumSource(ValuationMode.class)
    void un_depot_sur_une_banque_inconnue_est_refuse(ValuationMode mode) {
        LedgerFixture f = LedgerFixture.of(mode);

        OperationResult result = f.ledger.deposit(OperationId.random(), TestBankIds.random(), STONE, 1);

        assertThat(result.status()).isEqualTo(OperationStatus.UNKNOWN_BANK);
        assertThat(f.ledger.valueOf(TestBankIds.random())).isEmpty();
    }
}
