package dev.novadev.novabank.core.ledger;

import static dev.novadev.novabank.core.ledger.LedgerFixture.DIAMOND;
import static dev.novadev.novabank.core.ledger.LedgerFixture.STONE;
import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.api.OperationId;
import dev.novadev.novabank.api.operation.OperationResult;
import dev.novadev.novabank.api.operation.OperationStatus;
import dev.novadev.novabank.api.valuation.ValuationMode;
import dev.novadev.novabank.core.delivery.UndeliveredPolicy;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Rejouer une etape avec le meme identifiant d'operation n'a jamais de second effet.
 */
class IdempotenceTest {

    @ParameterizedTest
    @EnumSource(ValuationMode.class)
    void depot_rejoue(ValuationMode mode) {
        LedgerFixture f = LedgerFixture.of(mode);
        OperationId id = OperationId.random();

        OperationResult first = f.ledger.deposit(id, f.bankId, DIAMOND, 4);
        OperationResult replay = f.ledger.deposit(id, f.bankId, DIAMOND, 4);
        OperationResult replayOtherQuantity = f.ledger.deposit(id, f.bankId, STONE, 99);

        assertThat(first.status()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(replay.status()).isEqualTo(OperationStatus.DUPLICATE_OPERATION);
        assertThat(replay.isEffective()).isTrue();
        assertThat(replay.quantity()).isEqualTo(4L);
        assertThat(replay.valueDelta()).isEqualByComparingTo("10");
        assertThat(replayOtherQuantity.status()).isEqualTo(OperationStatus.DUPLICATE_OPERATION);
        assertThat(f.line(DIAMOND).quantity()).isEqualTo(4L);
        assertThat(f.line(STONE).quantity()).isZero();
        assertThat(f.bank().version()).isEqualTo(1L);
        assertThat(f.repository.journal()).hasSize(1);
    }

    @ParameterizedTest
    @EnumSource(ValuationMode.class)
    void reservation_rejouee(ValuationMode mode) {
        LedgerFixture f = LedgerFixture.of(mode);
        f.deposit(DIAMOND, 10);
        OperationId id = OperationId.random();

        f.reserve(id, DIAMOND, 3);
        OperationResult replay = f.reserve(id, DIAMOND, 3);

        assertThat(replay.status()).isEqualTo(OperationStatus.DUPLICATE_OPERATION);
        assertThat(replay.quantity()).isEqualTo(3L);
        assertThat(replay.valueDelta()).isEqualByComparingTo("-7.5");
        assertThat(f.line(DIAMOND).quantity()).isEqualTo(7L);
        assertThat(f.value()).isEqualByComparingTo("17.5");
    }

    @ParameterizedTest
    @EnumSource(ValuationMode.class)
    void confirmation_rejouee_avec_return_to_bank(ValuationMode mode) {
        LedgerFixture f = LedgerFixture.of(mode, UndeliveredPolicy.RETURN_TO_BANK);
        f.deposit(DIAMOND, 10);
        OperationId id = OperationId.random();
        f.reserve(id, DIAMOND, 6);

        OperationResult first = f.ledger.confirmWithdrawal(id, 2);
        long version = f.bank().version();
        OperationResult replay = f.ledger.confirmWithdrawal(id, 2);
        OperationResult replayOtherQuantity = f.ledger.confirmWithdrawal(id, 6);

        assertThat(first.status()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(replay.status()).isEqualTo(OperationStatus.DUPLICATE_OPERATION);
        assertThat(replay.quantity()).isEqualTo(2L);
        assertThat(replay.valueDelta()).isEqualByComparingTo(first.valueDelta());
        assertThat(replayOtherQuantity.status()).isEqualTo(OperationStatus.DUPLICATE_OPERATION);
        assertThat(f.line(DIAMOND).quantity()).isEqualTo(8L);
        assertThat(f.value()).isEqualByComparingTo("20");
        assertThat(f.bank().version()).isEqualTo(version);
    }

    @ParameterizedTest
    @EnumSource(ValuationMode.class)
    void confirmation_rejouee_avec_pending_delivery(ValuationMode mode) {
        LedgerFixture f = LedgerFixture.of(mode, UndeliveredPolicy.PENDING_DELIVERY);
        f.deposit(DIAMOND, 10);
        OperationId id = OperationId.random();
        f.reserve(id, DIAMOND, 6);

        f.ledger.confirmWithdrawal(id, 1);
        OperationResult replay = f.ledger.confirmWithdrawal(id, 1);

        assertThat(replay.status()).isEqualTo(OperationStatus.DUPLICATE_OPERATION);
        assertThat(f.deliveries.size()).isEqualTo(1);
        assertThat(f.deliveries.find(id)).hasValueSatisfying(d -> assertThat(d.quantity()).isEqualTo(5L));
        assertThat(f.line(DIAMOND).quantity()).isEqualTo(4L);
    }

    @ParameterizedTest
    @EnumSource(ValuationMode.class)
    void annulation_rejouee(ValuationMode mode) {
        LedgerFixture f = LedgerFixture.of(mode);
        f.deposit(DIAMOND, 10);
        OperationId id = OperationId.random();
        f.reserve(id, DIAMOND, 6);

        OperationResult first = f.ledger.cancelWithdrawal(id);
        OperationResult replay = f.ledger.cancelWithdrawal(id);

        assertThat(first.status()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(replay.status()).isEqualTo(OperationStatus.DUPLICATE_OPERATION);
        assertThat(replay.quantity()).isEqualTo(6L);
        assertThat(replay.valueDelta()).isEqualByComparingTo("15");
        assertThat(f.line(DIAMOND).quantity()).isEqualTo(10L);
        assertThat(f.value()).isEqualByComparingTo("25");
    }

    @ParameterizedTest
    @EnumSource(ValuationMode.class)
    void reset_rejoue(ValuationMode mode) {
        LedgerFixture f = LedgerFixture.of(mode);
        f.deposit(DIAMOND, 2);
        OperationId id = OperationId.random();
        f.ledger.reset(id, f.bankId);
        f.deposit(DIAMOND, 2);

        OperationResult replay = f.ledger.reset(id, f.bankId);

        assertThat(replay.status()).isEqualTo(OperationStatus.DUPLICATE_OPERATION);
        assertThat(replay.valueDelta()).isEqualByComparingTo("-5");
        assertThat(f.value()).isEqualByComparingTo("5");
    }
}
