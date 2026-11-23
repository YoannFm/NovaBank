package dev.novadev.novabank.core.ledger;

import dev.novadev.novabank.api.OperationId;
import dev.novadev.novabank.api.operation.OperationResult;
import dev.novadev.novabank.core.journal.OperationRecord;
import dev.novadev.novabank.core.journal.OperationState;
import dev.novadev.novabank.core.money.Money;
import dev.novadev.novabank.core.repository.ApplyOutcome;
import dev.novadev.novabank.core.repository.BankRepository;
import dev.novadev.novabank.core.valuation.StockAccounting;
import java.math.BigDecimal;
import java.util.function.Function;

/**
 * Traduit l'issue d'une mutation en {@link OperationResult} public.
 *
 * <p>Un rejeu renvoie les donnees de l'etape d'origine, relues dans le journal.
 */
final class OperationResults {

    private final BankRepository repository;
    private final StockAccounting accounting;

    OperationResults(BankRepository repository, StockAccounting accounting) {
        this.repository = repository;
        this.accounting = accounting;
    }

    /** Resultat d'un depot, d'une reservation ou d'un reset : le mouvement initial. */
    OperationResult ofInitialStep(OperationId id, ApplyOutcome outcome) {
        return of(id, outcome, record -> new Step(record.quantity(), record.valueDelta()));
    }

    /** Resultat d'une confirmation ou d'une annulation : livre ou rendu, et valeur re-creditee. */
    OperationResult ofSettlement(OperationId id, ApplyOutcome outcome) {
        return of(id, outcome, record -> new Step(
                record.state() == OperationState.RETURNED
                        ? record.withdrawal().returnedQuantity()
                        : record.withdrawal().deliveredQuantity(),
                record.withdrawal().returnedValue()));
    }

    private OperationResult of(OperationId id, ApplyOutcome outcome, Function<OperationRecord, Step> step) {
        if (outcome instanceof ApplyOutcome.Applied applied) {
            Step done = step.apply(applied.record());
            return OperationResult.success(id, done.quantity(), done.valueDelta(), accounting.bankValue(applied.bank()));
        }
        if (outcome instanceof ApplyOutcome.Duplicate duplicate) {
            Step done = step.apply(duplicate.record());
            return OperationResult.duplicate(id, done.quantity(), done.valueDelta(),
                    currentValue(duplicate.record()));
        }
        return OperationResult.rejected(id, ((ApplyOutcome.Rejected) outcome).status());
    }

    private BigDecimal currentValue(OperationRecord record) {
        return repository.findBank(record.bankId()).map(accounting::bankValue).orElse(Money.ZERO);
    }

    private record Step(long quantity, BigDecimal valueDelta) {
    }
}
