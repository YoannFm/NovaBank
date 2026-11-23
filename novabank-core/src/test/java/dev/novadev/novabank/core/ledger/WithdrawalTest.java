package dev.novadev.novabank.core.ledger;

import static dev.novadev.novabank.core.ledger.LedgerFixture.DIAMOND;
import static dev.novadev.novabank.core.ledger.LedgerFixture.PLAYER;
import static dev.novadev.novabank.core.ledger.LedgerFixture.STONE;
import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.api.OperationId;
import dev.novadev.novabank.api.operation.OperationResult;
import dev.novadev.novabank.api.operation.OperationStatus;
import dev.novadev.novabank.api.valuation.ValuationMode;
import dev.novadev.novabank.core.delivery.PendingDelivery;
import dev.novadev.novabank.core.delivery.UndeliveredPolicy;
import dev.novadev.novabank.core.journal.OperationRecord;
import dev.novadev.novabank.core.journal.OperationState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class WithdrawalTest {

    @ParameterizedTest
    @EnumSource(ValuationMode.class)
    void un_retrait_superieur_au_stock_est_refuse(ValuationMode mode) {
        LedgerFixture f = LedgerFixture.of(mode);
        f.deposit(STONE, 5);

        OperationResult result = f.reserve(OperationId.random(), STONE, 6);

        assertThat(result.status()).isEqualTo(OperationStatus.INSUFFICIENT_STOCK);
        assertThat(f.line(STONE).quantity()).isEqualTo(5L);
        assertThat(f.value()).isEqualByComparingTo("5");
        assertThat(f.repository.journal()).hasSize(1);
    }

    @Test
    void un_retrait_d_un_item_jamais_depose_est_refuse() {
        LedgerFixture f = LedgerFixture.of(ValuationMode.DERIVED);

        assertThat(f.reserve(OperationId.random(), DIAMOND, 1).status()).isEqualTo(OperationStatus.INSUFFICIENT_STOCK);
    }

    @ParameterizedTest
    @EnumSource(ValuationMode.class)
    void une_reservation_de_quantite_non_positive_est_refusee(ValuationMode mode) {
        LedgerFixture f = LedgerFixture.of(mode);
        f.deposit(STONE, 5);

        assertThat(f.reserve(OperationId.random(), STONE, 0).status()).isEqualTo(OperationStatus.INVALID_QUANTITY);
        assertThat(f.reserve(OperationId.random(), STONE, -3).status()).isEqualTo(OperationStatus.INVALID_QUANTITY);
    }

    @ParameterizedTest
    @EnumSource(ValuationMode.class)
    void un_retrait_exact_vide_la_ligne_et_la_valeur(ValuationMode mode) {
        LedgerFixture f = LedgerFixture.of(mode);
        f.deposit(DIAMOND, 4);
        OperationId id = OperationId.random();

        OperationResult reserved = f.reserve(id, DIAMOND, 4);
        OperationResult confirmed = f.ledger.confirmWithdrawal(id, 4);

        assertThat(reserved.status()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(reserved.quantity()).isEqualTo(4L);
        assertThat(reserved.valueDelta()).isEqualByComparingTo("-10");
        assertThat(confirmed.status()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(confirmed.quantity()).isEqualTo(4L);
        assertThat(confirmed.valueDelta()).isZero();
        assertThat(f.bank().lines()).isEmpty();
        assertThat(f.value()).isZero();
        assertThat(f.record(id).state()).isEqualTo(OperationState.COMMITTED);
        assertThat(f.deliveries.size()).isZero();
    }

    @ParameterizedTest
    @EnumSource(ValuationMode.class)
    void return_to_bank_recredite_exactement_la_part_non_livree(ValuationMode mode) {
        LedgerFixture f = LedgerFixture.of(mode, UndeliveredPolicy.RETURN_TO_BANK);
        f.deposit(DIAMOND, 10);
        OperationId id = OperationId.random();
        f.reserve(id, DIAMOND, 6);

        OperationResult confirmed = f.ledger.confirmWithdrawal(id, 4);

        assertThat(confirmed.status()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(confirmed.quantity()).isEqualTo(4L);
        assertThat(confirmed.valueDelta()).isEqualByComparingTo("5");
        assertThat(f.line(DIAMOND).quantity()).isEqualTo(6L);
        assertThat(f.line(DIAMOND).countedQuantity()).isEqualTo(6L);
        assertThat(f.value()).isEqualByComparingTo("15");
        OperationRecord record = f.record(id);
        assertThat(record.state()).isEqualTo(OperationState.COMMITTED);
        assertThat(record.withdrawal().deliveredQuantity()).isEqualTo(4L);
        assertThat(record.withdrawal().returnedQuantity()).isEqualTo(2L);
        assertThat(record.undeliveredQuantity()).isZero();
        assertThat(f.deliveries.size()).isZero();
    }

    @ParameterizedTest
    @EnumSource(ValuationMode.class)
    void pending_delivery_cree_une_livraison_sans_recrediter(ValuationMode mode) {
        LedgerFixture f = LedgerFixture.of(mode, UndeliveredPolicy.PENDING_DELIVERY);
        f.deposit(DIAMOND, 10);
        OperationId id = OperationId.random();
        f.reserve(id, DIAMOND, 6);

        OperationResult confirmed = f.ledger.confirmWithdrawal(id, 4);

        assertThat(confirmed.status()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(confirmed.valueDelta()).isZero();
        assertThat(f.line(DIAMOND).quantity()).isEqualTo(4L);
        assertThat(f.value()).isEqualByComparingTo("10");
        assertThat(f.deliveries.find(id)).contains(new PendingDelivery(id, PLAYER, DIAMOND, 2L));
        assertThat(f.deliveries.pendingFor(PLAYER)).hasSize(1);
        assertThat(f.record(id).undeliveredQuantity()).isEqualTo(2L);
    }

    @ParameterizedTest
    @EnumSource(ValuationMode.class)
    void une_annulation_rend_tout_a_la_banque(ValuationMode mode) {
        LedgerFixture f = LedgerFixture.of(mode);
        f.deposit(DIAMOND, 10);
        OperationId id = OperationId.random();
        f.reserve(id, DIAMOND, 7);

        OperationResult cancelled = f.ledger.cancelWithdrawal(id);

        assertThat(cancelled.status()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(cancelled.quantity()).isEqualTo(7L);
        assertThat(cancelled.valueDelta()).isEqualByComparingTo("17.5");
        assertThat(f.line(DIAMOND).quantity()).isEqualTo(10L);
        assertThat(f.line(DIAMOND).countedQuantity()).isEqualTo(10L);
        assertThat(f.value()).isEqualByComparingTo("25");
        assertThat(f.record(id).state()).isEqualTo(OperationState.RETURNED);
    }

    @Test
    void en_mode_cumule_le_recredit_ignore_le_prix_courant() {
        LedgerFixture f = LedgerFixture.of(ValuationMode.ACCUMULATED);
        f.deposit(DIAMOND, 10);
        OperationId id = OperationId.random();
        f.reserve(id, DIAMOND, 4);
        f.catalog.price(DIAMOND, "100");

        f.ledger.cancelWithdrawal(id);

        assertThat(f.value()).isEqualByComparingTo("25");
    }

    @Test
    void en_mode_derive_le_recredit_suit_le_prix_courant() {
        LedgerFixture f = LedgerFixture.of(ValuationMode.DERIVED);
        f.deposit(DIAMOND, 10);
        OperationId id = OperationId.random();
        f.reserve(id, DIAMOND, 4);
        f.catalog.price(DIAMOND, "3");

        f.ledger.cancelWithdrawal(id);

        assertThat(f.value()).isEqualByComparingTo("30");
    }

    @Test
    void une_confirmation_ou_annulation_d_operation_inconnue_est_refusee() {
        LedgerFixture f = LedgerFixture.of(ValuationMode.DERIVED);
        OperationId deposit = OperationId.random();
        f.ledger.deposit(deposit, f.bankId, STONE, 3);

        assertThat(f.ledger.confirmWithdrawal(OperationId.random(), 1).status())
                .isEqualTo(OperationStatus.UNKNOWN_OPERATION);
        assertThat(f.ledger.cancelWithdrawal(OperationId.random()).status())
                .isEqualTo(OperationStatus.UNKNOWN_OPERATION);
        assertThat(f.ledger.confirmWithdrawal(deposit, 1).status()).isEqualTo(OperationStatus.UNKNOWN_OPERATION);
        assertThat(f.ledger.cancelWithdrawal(deposit).status()).isEqualTo(OperationStatus.UNKNOWN_OPERATION);
    }

    @Test
    void une_quantite_livree_incoherente_est_refusee() {
        LedgerFixture f = LedgerFixture.of(ValuationMode.DERIVED);
        f.deposit(STONE, 5);
        OperationId id = OperationId.random();
        f.reserve(id, STONE, 3);

        assertThat(f.ledger.confirmWithdrawal(id, 4).status()).isEqualTo(OperationStatus.INVALID_QUANTITY);
        assertThat(f.ledger.confirmWithdrawal(id, -1).status()).isEqualTo(OperationStatus.INVALID_QUANTITY);
        assertThat(f.record(id).state()).isEqualTo(OperationState.RESERVED);
    }

    @Test
    void un_retrait_solde_ne_peut_pas_etre_solde_dans_l_autre_sens() {
        LedgerFixture f = LedgerFixture.of(ValuationMode.DERIVED);
        f.deposit(STONE, 10);
        OperationId confirmed = OperationId.random();
        OperationId cancelled = OperationId.random();
        f.reserve(confirmed, STONE, 2);
        f.reserve(cancelled, STONE, 2);
        f.ledger.confirmWithdrawal(confirmed, 2);
        f.ledger.cancelWithdrawal(cancelled);

        assertThat(f.ledger.cancelWithdrawal(confirmed).status()).isEqualTo(OperationStatus.ALREADY_SETTLED);
        assertThat(f.ledger.confirmWithdrawal(cancelled, 2).status()).isEqualTo(OperationStatus.ALREADY_SETTLED);
        assertThat(f.line(STONE).quantity()).isEqualTo(8L);
    }

    @ParameterizedTest
    @EnumSource(ValuationMode.class)
    void un_item_retire_du_catalogue_reste_retirable(ValuationMode mode) {
        LedgerFixture f = LedgerFixture.of(mode);
        f.deposit(DIAMOND, 4);
        f.deposit(STONE, 2);
        f.catalog.remove(DIAMOND);

        Expected expected = mode == ValuationMode.DERIVED
                ? new Expected("2", "0") : new Expected("12", "-10");
        assertThat(f.value()).isEqualByComparingTo(expected.valueBefore());
        assertThat(f.deposit(DIAMOND, 1).status()).isEqualTo(OperationStatus.NOT_ACCEPTED);

        OperationResult withdrawn = f.withdraw(DIAMOND, 4);

        assertThat(withdrawn.status()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(withdrawn.valueDelta()).isEqualByComparingTo(expected.withdrawDelta());
        assertThat(f.value()).isEqualByComparingTo("2");
        assertThat(f.line(DIAMOND).quantity()).isZero();
    }

    @Test
    void un_deficit_sous_la_base_ne_permet_pas_de_regenerer_de_valeur() {
        LedgerFixture f = LedgerFixture.of(ValuationMode.ACCUMULATED);
        f.deposit(STONE, 5);
        f.reset();
        f.deposit(STONE, 5);
        OperationId counted = OperationId.random();
        OperationId uncounted = OperationId.random();
        f.reserve(counted, STONE, 5);
        f.reserve(uncounted, STONE, 5);

        f.ledger.cancelWithdrawal(counted);
        f.ledger.confirmWithdrawal(uncounted, 5);
        f.deposit(STONE, 5);

        assertThat(f.line(STONE).quantity()).isEqualTo(10L);
        assertThat(f.line(STONE).baselineQuantity()).isEqualTo(5L);
        assertThat(f.value()).isEqualByComparingTo("5");
    }

    private record Expected(String valueBefore, String withdrawDelta) {
    }
}
