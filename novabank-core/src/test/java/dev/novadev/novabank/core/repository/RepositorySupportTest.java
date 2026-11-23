package dev.novadev.novabank.core.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.novadev.novabank.api.OperationId;
import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.api.operation.OperationStatus;
import dev.novadev.novabank.api.valuation.ValuationMode;
import dev.novadev.novabank.core.bank.Bank;
import dev.novadev.novabank.core.journal.OperationRecord;
import dev.novadev.novabank.core.testing.TestBankIds;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RepositorySupportTest {

    private static final Bank BANK = Bank.open(TestBankIds.random(), EntityRef.player(UUID.randomUUID()),
            ValuationMode.DERIVED);
    private static final OperationId ID = OperationId.random();
    private static final OperationRecord RECORD = OperationRecord.deposit(ID, BANK.id(), ItemKey.minecraft("stone"),
            1L, 1L, BigDecimal.ONE, Instant.EPOCH);

    @Test
    void successor_incremente_la_version_et_refuse_un_changement_d_identite() {
        assertThat(RepositorySupport.successor(BANK, BANK).version()).isEqualTo(1L);
        Bank otherMode = new Bank(BANK.id(), BANK.owner(), ValuationMode.ACCUMULATED, 0L, 0L, BANK.lines());
        Bank otherId = Bank.open(TestBankIds.random(), BANK.owner(), BANK.valuationMode());

        assertThatThrownBy(() -> RepositorySupport.successor(BANK, otherMode)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> RepositorySupport.successor(BANK, otherId)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void requireSameTarget_refuse_une_autre_banque_ou_operation() {
        RepositorySupport.requireSameTarget(RECORD, ID, BANK.id());

        assertThatThrownBy(() -> RepositorySupport.requireSameTarget(RECORD, ID, TestBankIds.random()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> RepositorySupport.requireSameTarget(RECORD, OperationId.random(), BANK.id()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void outcomeWithoutChange_traduit_refus_et_rejeu_et_refuse_une_acceptation() {
        assertThat(RepositorySupport.outcomeWithoutChange(MutationDecision.reject(OperationStatus.NOT_ACCEPTED), null))
                .isEqualTo(new ApplyOutcome.Rejected(OperationStatus.NOT_ACCEPTED));
        assertThat(RepositorySupport.outcomeWithoutChange(MutationDecision.alreadyApplied(), RECORD))
                .isEqualTo(new ApplyOutcome.Duplicate(RECORD));
        assertThatThrownBy(() -> RepositorySupport.outcomeWithoutChange(MutationDecision.alreadyApplied(), null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> RepositorySupport.outcomeWithoutChange(MutationDecision.accept(BANK, RECORD), RECORD))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void storageException_conserve_message_et_cause() {
        IllegalStateException cause = new IllegalStateException("origine");

        assertThat(new StorageException("panne", cause)).hasMessage("panne").hasCause(cause);
        assertThat(new StorageException("panne")).hasMessage("panne").hasNoCause();
    }
}
