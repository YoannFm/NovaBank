package dev.novadev.novabank.core.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.api.OperationId;
import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.api.operation.OperationStatus;
import dev.novadev.novabank.api.valuation.ValuationMode;
import dev.novadev.novabank.core.bank.Bank;
import dev.novadev.novabank.core.bank.StockLine;
import dev.novadev.novabank.core.journal.OperationRecord;
import dev.novadev.novabank.core.journal.OperationState;
import dev.novadev.novabank.core.money.Money;
import dev.novadev.novabank.core.testing.ConcurrentRunner;
import dev.novadev.novabank.core.testing.TestBankIds;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Contrat commun a toutes les implementations de {@link BankRepository}.
 *
 * <p>Chaque backend etend cette classe et fournit un depot vide via
 * {@link #newRepository()}. Les mutations utilisees ici sont volontairement simples
 * (une unite de stock par operation) : le contrat porte sur l'atomicite,
 * l'idempotence et la version, pas sur les regles metier.
 */
public abstract class BankRepositoryContractTest {

    private static final ItemKey STONE = ItemKey.minecraft("stone");
    private static final EntityRef OWNER = new EntityRef(EntityRef.PLAYER, "owner");
    private static final Instant AT = Instant.parse("2026-10-22T12:00:00Z");

    private BankRepository repository;
    private BankId bankId;

    /**
     * @return un depot vide, propre a chaque test
     */
    protected abstract BankRepository newRepository();

    @BeforeEach
    void openBank() {
        repository = newRepository();
        bankId = TestBankIds.random();
        repository.createBank(bankId, OWNER, ValuationMode.ACCUMULATED);
    }

    @Test
    void createBank_est_idempotente_et_conserve_la_banque_existante() {
        depositOne(OperationId.random());

        Bank again = repository.createBank(bankId, new EntityRef("faction", "other"), ValuationMode.DERIVED);

        assertThat(again.owner()).isEqualTo(OWNER);
        assertThat(again.valuationMode()).isEqualTo(ValuationMode.ACCUMULATED);
        assertThat(again.version()).isEqualTo(1L);
        assertThat(again.line(STONE).quantity()).isEqualTo(1L);
    }

    @Test
    void une_banque_neuve_est_vide_en_version_0() {
        Bank bank = repository.findBank(bankId).orElseThrow();

        assertThat(bank.version()).isZero();
        assertThat(bank.lines()).isEmpty();
        assertThat(repository.findBank(TestBankIds.random())).isEmpty();
    }

    @Test
    void apply_accepte_enregistre_banque_et_journal_et_incremente_la_version() {
        OperationId id = OperationId.random();

        ApplyOutcome outcome = depositOne(id);

        assertThat(outcome).isInstanceOf(ApplyOutcome.Applied.class);
        ApplyOutcome.Applied applied = (ApplyOutcome.Applied) outcome;
        Bank stored = repository.findBank(bankId).orElseThrow();
        assertThat(stored.version()).isEqualTo(1L);
        assertThat(stored.line(STONE).quantity()).isEqualTo(1L);
        assertThat(applied.bank()).isEqualTo(stored);
        assertThat(repository.findOperation(id)).contains(applied.record());
    }

    @Test
    void l_epoch_de_reset_est_persistee_avec_la_banque() {
        assertThat(repository.findBank(bankId).orElseThrow().resetEpoch()).isZero();
        OperationId id = OperationId.random();

        repository.apply(id, bankId, bank -> MutationDecision.accept(bank.reset(),
                OperationRecord.reset(id, bankId, 0L, 0L, BigDecimal.ZERO, AT)));

        Bank stored = repository.findBank(bankId).orElseThrow();
        assertThat(stored.resetEpoch()).isEqualTo(1L);
        assertThat(stored.version()).isEqualTo(1L);
        assertThat(repository.createBank(bankId, OWNER, ValuationMode.ACCUMULATED).resetEpoch()).isEqualTo(1L);
    }

    @Test
    void apply_refuse_ne_change_rien() {
        OperationId id = OperationId.random();

        ApplyOutcome outcome = repository.apply(id, bankId,
                bank -> MutationDecision.reject(OperationStatus.INSUFFICIENT_STOCK));

        assertThat(outcome).isEqualTo(new ApplyOutcome.Rejected(OperationStatus.INSUFFICIENT_STOCK));
        assertThat(repository.findBank(bankId).orElseThrow().version()).isZero();
        assertThat(repository.findOperation(id)).isEmpty();
    }

    @Test
    void apply_sur_banque_inconnue_est_refuse_sans_appeler_la_mutation() {
        AtomicInteger calls = new AtomicInteger();
        OperationId id = OperationId.random();

        ApplyOutcome outcome = repository.apply(id, TestBankIds.random(), countingDeposit(id, calls));

        assertThat(outcome).isEqualTo(new ApplyOutcome.Rejected(OperationStatus.UNKNOWN_BANK));
        assertThat(calls).hasValue(0);
    }

    @Test
    void apply_rejoue_renvoie_l_entree_d_origine_sans_appeler_la_mutation() {
        OperationId id = OperationId.random();
        ApplyOutcome.Applied first = (ApplyOutcome.Applied) depositOne(id);
        AtomicInteger calls = new AtomicInteger();

        ApplyOutcome replay = repository.apply(id, bankId, countingDeposit(id, calls));

        assertThat(replay).isEqualTo(new ApplyOutcome.Duplicate(first.record()));
        assertThat(calls).hasValue(0);
        assertThat(repository.findBank(bankId).orElseThrow().version()).isEqualTo(1L);
    }

    @Test
    void un_identifiant_d_operation_est_unique_toutes_banques_confondues() {
        BankId other = TestBankIds.random();
        repository.createBank(other, OWNER, ValuationMode.DERIVED);
        OperationId id = OperationId.random();
        depositOne(id);

        ApplyOutcome onOther = repository.apply(id, other, oneStone(id));

        assertThat(onOther).isInstanceOf(ApplyOutcome.Duplicate.class);
        assertThat(repository.findBank(other).orElseThrow().version()).isZero();
    }

    @Test
    void apply_refuse_une_entree_qui_ne_vise_pas_l_operation_demandee() {
        OperationId id = OperationId.random();

        assertThatThrownBy(() -> repository.apply(id, bankId, oneStone(OperationId.random())))
                .isInstanceOf(IllegalStateException.class);
        assertThat(repository.findOperation(id)).isEmpty();
        assertThat(repository.findBank(bankId).orElseThrow().version()).isZero();
    }

    @Test
    void une_exception_de_la_mutation_annule_l_unite() {
        OperationId id = OperationId.random();

        assertThatThrownBy(() -> repository.apply(id, bankId, bank -> {
            throw new IllegalArgumentException("boom");
        })).isInstanceOf(IllegalArgumentException.class);
        assertThat(repository.findOperation(id)).isEmpty();
        assertThat(repository.findBank(bankId).orElseThrow().version()).isZero();
    }

    @Test
    void settle_sur_operation_inconnue_est_refuse() {
        ApplyOutcome outcome = repository.settle(OperationId.random(),
                (bank, record) -> MutationDecision.alreadyApplied());

        assertThat(outcome).isEqualTo(new ApplyOutcome.Rejected(OperationStatus.UNKNOWN_OPERATION));
    }

    @Test
    void settle_accepte_remplace_l_entree_et_incremente_la_version() {
        OperationId id = reserveOneStone();

        ApplyOutcome outcome = repository.settle(id, (bank, record) -> MutationDecision.accept(bank,
                record.settle(OperationState.COMMITTED, 1L, 0L, Money.ZERO)));

        assertThat(outcome).isInstanceOf(ApplyOutcome.Applied.class);
        assertThat(repository.findOperation(id).orElseThrow().state()).isEqualTo(OperationState.COMMITTED);
        assertThat(repository.findBank(bankId).orElseThrow().version()).isEqualTo(3L);
    }

    @Test
    void settle_deja_applique_renvoie_l_entree_courante_sans_rien_changer() {
        OperationId id = reserveOneStone();
        OperationRecord reserved = repository.findOperation(id).orElseThrow();

        ApplyOutcome outcome = repository.settle(id, (bank, record) -> MutationDecision.alreadyApplied());

        assertThat(outcome).isEqualTo(new ApplyOutcome.Duplicate(reserved));
        assertThat(repository.findBank(bankId).orElseThrow().version()).isEqualTo(2L);
    }

    @Test
    void settle_refuse_ne_change_rien() {
        OperationId id = reserveOneStone();

        ApplyOutcome outcome = repository.settle(id,
                (bank, record) -> MutationDecision.reject(OperationStatus.ALREADY_SETTLED));

        assertThat(outcome).isEqualTo(new ApplyOutcome.Rejected(OperationStatus.ALREADY_SETTLED));
        assertThat(repository.findOperation(id).orElseThrow().state()).isEqualTo(OperationState.RESERVED);
        assertThat(repository.findBank(bankId).orElseThrow().version()).isEqualTo(2L);
    }

    @Test
    void les_mutations_concurrentes_ne_perdent_aucune_mise_a_jour() throws Exception {
        int threads = 16;
        int perThread = 100;

        ConcurrentRunner.run(threads, index -> {
            for (int i = 0; i < perThread; i++) {
                depositOne(OperationId.random());
            }
        });

        Bank bank = repository.findBank(bankId).orElseThrow();
        assertThat(bank.line(STONE).quantity()).isEqualTo((long) threads * perThread);
        assertThat(bank.version()).isEqualTo((long) threads * perThread);
    }

    @Test
    void le_rejeu_concurrent_d_une_operation_n_a_qu_un_effet() throws Exception {
        OperationId id = OperationId.random();
        AtomicInteger applied = new AtomicInteger();
        AtomicInteger duplicates = new AtomicInteger();

        ConcurrentRunner.run(16, index -> {
            ApplyOutcome outcome = depositOne(id);
            (outcome instanceof ApplyOutcome.Applied ? applied : duplicates).incrementAndGet();
        });

        assertThat(applied).hasValue(1);
        assertThat(duplicates).hasValue(15);
        assertThat(repository.findBank(bankId).orElseThrow().line(STONE).quantity()).isEqualTo(1L);
    }

    @Test
    void une_mutation_qui_change_l_identite_de_la_banque_est_refusee() {
        OperationId id = OperationId.random();

        assertThatThrownBy(() -> repository.apply(id, bankId, bank -> MutationDecision.accept(
                new Bank(bank.id(), new EntityRef("faction", "other"), bank.valuationMode(), bank.version(),
                        bank.resetEpoch(), bank.lines()),
                OperationRecord.deposit(id, bankId, STONE, 1L, 1L, BigDecimal.ONE, AT))))
                .isInstanceOf(IllegalStateException.class);
        assertThat(repository.findOperation(id)).isEmpty();
        assertThat(repository.findBank(bankId).orElseThrow().owner()).isEqualTo(OWNER);
    }

    @Test
    void les_montants_et_les_lignes_sont_relus_a_l_identique() {
        ItemKey gold = ItemKey.minecraft("gold_block");
        BigDecimal tiny = new BigDecimal("0.0001");
        BigDecimal huge = new BigDecimal("123456789012345.6789");
        OperationId id = OperationId.random();

        ApplyOutcome.Applied applied = (ApplyOutcome.Applied) repository.apply(id, bankId,
                bank -> MutationDecision.accept(bank
                                .withLine(STONE, new StockLine(7L, 3L, tiny))
                                .withLine(gold, new StockLine(Long.MAX_VALUE, 0L, huge)),
                        OperationRecord.deposit(id, bankId, gold, 5L, 2L, huge, AT)));

        Bank stored = repository.findBank(bankId).orElseThrow();
        assertThat(stored).isEqualTo(applied.bank());
        assertThat(stored.line(STONE).accumulatedValue()).isEqualTo(tiny.setScale(Money.SCALE));
        assertThat(stored.line(gold).accumulatedValue().toPlainString()).isEqualTo("123456789012345.6789");
        assertThat(stored.line(gold).quantity()).isEqualTo(Long.MAX_VALUE);
        assertThat(repository.findOperation(id).orElseThrow().valueDelta().toPlainString())
                .isEqualTo("123456789012345.6789");
    }

    @Test
    void une_ligne_videe_disparait_de_la_banque() {
        depositOne(OperationId.random());
        OperationId id = OperationId.random();

        repository.apply(id, bankId, bank -> MutationDecision.accept(bank.withLine(STONE, bank.line(STONE).minus(1L)),
                OperationRecord.reservation(id, bankId, STONE, 1L, 1L, BigDecimal.ONE.negate(), AT,
                        UUID.randomUUID(), 0L)));

        Bank stored = repository.findBank(bankId).orElseThrow();
        assertThat(stored.lines()).isEmpty();
        assertThat(stored.version()).isEqualTo(2L);
    }

    @Test
    void reservedWithdrawals_liste_les_retraits_reserves_par_ordre_chronologique() {
        for (int i = 0; i < 3; i++) {
            depositOne(OperationId.random());
        }
        OperationId late = reserveOneStoneAt(AT.plusSeconds(20));
        OperationId early = reserveOneStoneAt(AT.plusSeconds(5));
        OperationId settled = reserveOneStoneAt(AT.plusSeconds(10));
        repository.settle(settled, (bank, record) -> MutationDecision.accept(bank,
                record.settle(OperationState.COMMITTED, 1L, 0L, Money.ZERO)));

        List<OperationRecord> reserved = repository.reservedWithdrawals();

        assertThat(reserved).extracting(OperationRecord::operationId).containsExactly(early, late);
        assertThat(reserved).allMatch(record -> record.state() == OperationState.RESERVED);
        assertThat(reserved.get(0)).isEqualTo(repository.findOperation(early).orElseThrow());
    }

    @Test
    void reservedWithdrawals_est_vide_sans_retrait_en_cours() {
        depositOne(OperationId.random());

        assertThat(repository.reservedWithdrawals()).isEmpty();
    }

    @Test
    void history_liste_le_journal_d_une_banque_du_plus_recent_au_plus_ancien_par_page() {
        OperationId first = depositOneAt(AT);
        OperationId second = depositOneAt(AT.plusSeconds(10));
        OperationId third = depositOneAt(AT.plusSeconds(20));
        BankId otherBank = TestBankIds.random();
        repository.createBank(otherBank, new EntityRef("faction", "autre"), ValuationMode.DERIVED);
        OperationId elsewhere = OperationId.random();
        repository.apply(elsewhere, otherBank, oneStone(elsewhere));

        assertThat(repository.history(bankId, 0, 10)).extracting(OperationRecord::operationId)
                .containsExactly(third, second, first);
        assertThat(repository.history(bankId, 1, 1)).extracting(OperationRecord::operationId).containsExactly(second);
        assertThat(repository.history(bankId, 3, 5)).isEmpty();
        assertThat(repository.history(bankId, 0, 1).get(0)).isEqualTo(repository.findOperation(third).orElseThrow());
        assertThat(repository.history(TestBankIds.random(), 0, 10)).isEmpty();
    }

    @Test
    void history_refuse_une_page_invalide() {
        assertThatThrownBy(() -> repository.history(bankId, -1, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.history(bankId, 0, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    private OperationId depositOneAt(Instant at) {
        OperationId id = OperationId.random();
        repository.apply(id, bankId, bank -> MutationDecision.accept(bank.withLine(STONE, bank.line(STONE).plus(1L)),
                OperationRecord.deposit(id, bank.id(), STONE, 1L, 1L, BigDecimal.ONE, at)));
        return id;
    }

    private ApplyOutcome depositOne(OperationId id) {
        return repository.apply(id, bankId, oneStone(id));
    }

    private OperationId reserveOneStone() {
        depositOne(OperationId.random());
        return reserveOneStoneAt(AT);
    }

    private OperationId reserveOneStoneAt(Instant at) {
        OperationId id = OperationId.random();
        ApplyOutcome outcome = repository.apply(id, bankId, bank -> MutationDecision.accept(
                bank.withLine(STONE, bank.line(STONE).minus(1L)),
                OperationRecord.reservation(id, bankId, STONE, 1L, 1L, BigDecimal.ONE.negate(), at,
                        UUID.randomUUID(), bank.resetEpoch())));
        assertThat(outcome).isInstanceOf(ApplyOutcome.Applied.class);
        return id;
    }

    private static BankMutation oneStone(OperationId id) {
        return bank -> MutationDecision.accept(bank.withLine(STONE, bank.line(STONE).plus(1L)),
                OperationRecord.deposit(id, bank.id(), STONE, 1L, 1L, BigDecimal.ONE, AT));
    }

    private static BankMutation countingDeposit(OperationId id, AtomicInteger calls) {
        return bank -> {
            calls.incrementAndGet();
            return oneStone(id).decide(bank);
        };
    }
}
