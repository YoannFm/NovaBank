package dev.novadev.novabank.core.ledger;

import static dev.novadev.novabank.core.ledger.LedgerFixture.DIAMOND;
import static dev.novadev.novabank.core.ledger.LedgerFixture.STONE;
import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.api.OperationId;
import dev.novadev.novabank.api.operation.OperationResult;
import dev.novadev.novabank.api.operation.OperationStatus;
import dev.novadev.novabank.api.operation.OperationType;
import dev.novadev.novabank.api.valuation.ValuationMode;
import dev.novadev.novabank.core.bank.StockLine;
import dev.novadev.novabank.core.delivery.UndeliveredPolicy;
import dev.novadev.novabank.core.journal.OperationRecord;
import dev.novadev.novabank.core.testing.TestBankIds;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ResetTest {

    @ParameterizedTest
    @EnumSource(ValuationMode.class)
    void un_reset_efface_la_valeur_sans_toucher_au_stock(ValuationMode mode) {
        LedgerFixture f = LedgerFixture.of(mode);
        f.deposit(STONE, 6);
        f.deposit(DIAMOND, 2);
        OperationId id = OperationId.random();

        OperationResult result = f.ledger.reset(id, f.bankId);

        assertThat(result.status()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(result.valueDelta()).isEqualByComparingTo("-11");
        assertThat(f.value()).isZero();
        assertThat(f.line(STONE)).isEqualTo(new StockLine(6, 6, BigDecimal.ZERO));
        assertThat(f.line(DIAMOND).quantity()).isEqualTo(2L);
        OperationRecord record = f.record(id);
        assertThat(record.type()).isEqualTo(OperationType.RESET);
        assertThat(record.itemKey()).isNull();
        assertThat(record.quantity()).isEqualTo(8L);
        assertThat(record.countedQuantity()).isEqualTo(8L);
    }

    @ParameterizedTest
    @EnumSource(ValuationMode.class)
    void retirer_puis_redeposer_apres_reset_ne_regenere_aucune_valeur(ValuationMode mode) {
        LedgerFixture f = LedgerFixture.of(mode);
        f.deposit(DIAMOND, 10);
        f.reset();

        OperationResult withdrawn = f.withdraw(DIAMOND, 10);
        OperationResult redeposited = f.deposit(DIAMOND, 10);

        assertThat(withdrawn.valueDelta()).isZero();
        assertThat(redeposited.valueDelta()).isZero();
        assertThat(f.value()).isZero();
        assertThat(f.line(DIAMOND).countedQuantity()).isZero();
    }

    @ParameterizedTest
    @EnumSource(ValuationMode.class)
    void une_ligne_videe_sous_sa_base_est_conservee(ValuationMode mode) {
        LedgerFixture f = LedgerFixture.of(mode);
        f.deposit(STONE, 10);
        f.reset();

        f.withdraw(STONE, 10);

        assertThat(f.bank().lines()).containsKey(STONE);
        assertThat(f.line(STONE).quantity()).isZero();
        assertThat(f.line(STONE).baselineQuantity()).isEqualTo(10L);
    }

    @ParameterizedTest
    @EnumSource(ValuationMode.class)
    void un_depot_apres_reset_compte_seulement_au_dela_de_la_base(ValuationMode mode) {
        LedgerFixture f = LedgerFixture.of(mode);
        f.deposit(DIAMOND, 10);
        f.reset();
        f.withdraw(DIAMOND, 4);

        OperationResult result = f.deposit(DIAMOND, 7);

        assertThat(f.line(DIAMOND).quantity()).isEqualTo(13L);
        assertThat(f.line(DIAMOND).countedQuantity()).isEqualTo(3L);
        assertThat(result.valueDelta()).isEqualByComparingTo("7.5");
        assertThat(f.value()).isEqualByComparingTo("7.5");
    }

    @ParameterizedTest
    @EnumSource(ValuationMode.class)
    void un_retrait_apres_reset_consomme_d_abord_les_unites_comptees(ValuationMode mode) {
        LedgerFixture f = LedgerFixture.of(mode);
        f.deposit(STONE, 10);
        f.reset();
        f.deposit(STONE, 3);

        OperationResult withdrawn = f.withdraw(STONE, 5);

        assertThat(withdrawn.valueDelta()).isEqualByComparingTo("-3");
        assertThat(f.value()).isZero();
        assertThat(f.line(STONE).quantity()).isEqualTo(8L);
    }

    @ParameterizedTest
    @EnumSource(ValuationMode.class)
    void annuler_un_retrait_reserve_avant_un_reset_ne_recree_aucune_valeur(ValuationMode mode) {
        LedgerFixture f = LedgerFixture.of(mode);
        f.deposit(DIAMOND, 10);
        OperationId id = OperationId.random();
        f.reserve(id, DIAMOND, 4);
        f.reset();

        OperationResult cancelled = f.ledger.cancelWithdrawal(id);

        assertThat(cancelled.status()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(cancelled.quantity()).isEqualTo(4L);
        assertThat(cancelled.valueDelta()).isZero();
        assertThat(f.value()).isZero();
        assertThat(f.line(DIAMOND).quantity()).isEqualTo(10L);
        assertThat(f.line(DIAMOND).countedQuantity()).isZero();
        assertThat(f.withdraw(DIAMOND, 10).valueDelta()).isZero();
        assertThat(f.deposit(DIAMOND, 10).valueDelta()).isZero();
    }

    @ParameterizedTest
    @EnumSource(ValuationMode.class)
    void une_restitution_partielle_apres_reset_ne_recree_aucune_valeur(ValuationMode mode) {
        LedgerFixture f = LedgerFixture.of(mode, UndeliveredPolicy.RETURN_TO_BANK);
        f.deposit(DIAMOND, 10);
        OperationId id = OperationId.random();
        f.reserve(id, DIAMOND, 6);
        f.reset();

        OperationResult confirmed = f.ledger.confirmWithdrawal(id, 2);

        assertThat(confirmed.status()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(confirmed.valueDelta()).isZero();
        assertThat(f.value()).isZero();
        assertThat(f.line(DIAMOND).quantity()).isEqualTo(8L);
        assertThat(f.line(DIAMOND).countedQuantity()).isZero();
        assertThat(f.record(id).withdrawal().returnedQuantity()).isEqualTo(4L);
    }

    @ParameterizedTest
    @EnumSource(ValuationMode.class)
    void une_restitution_apres_un_reset_anterieur_a_la_reservation_reste_exacte(ValuationMode mode) {
        LedgerFixture f = LedgerFixture.of(mode);
        f.deposit(DIAMOND, 4);
        f.reset();
        f.deposit(DIAMOND, 6);
        OperationId id = OperationId.random();
        f.reserve(id, DIAMOND, 8);

        f.ledger.cancelWithdrawal(id);

        assertThat(f.line(DIAMOND).quantity()).isEqualTo(10L);
        assertThat(f.line(DIAMOND).countedQuantity()).isEqualTo(6L);
        assertThat(f.value()).isEqualByComparingTo("15");
        assertThat(f.bank().resetEpoch()).isEqualTo(1L);
    }

    @Test
    void un_reset_de_banque_vide_ou_inconnue() {
        LedgerFixture f = LedgerFixture.of(ValuationMode.ACCUMULATED);

        assertThat(f.reset().status()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(f.value()).isZero();
        assertThat(f.ledger.reset(OperationId.random(), TestBankIds.random()).status())
                .isEqualTo(OperationStatus.UNKNOWN_BANK);
    }
}
