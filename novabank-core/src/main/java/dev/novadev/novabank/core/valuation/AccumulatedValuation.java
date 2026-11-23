package dev.novadev.novabank.core.valuation;

import dev.novadev.novabank.api.valuation.ValuationMode;
import dev.novadev.novabank.core.bank.StockLine;
import dev.novadev.novabank.core.money.Money;
import java.math.BigDecimal;
import java.util.Optional;

/**
 * Mode {@link ValuationMode#ACCUMULATED} : la ligne conserve la valeur de ses unites
 * comptees. Un retrait deduit leur valeur moyenne ; la derniere unite comptee emporte
 * exactement le reste, sans residu d'arrondi. Le prix courant est ignore.
 */
final class AccumulatedValuation implements ValuationStrategy {

    @Override
    public ValuationMode mode() {
        return ValuationMode.ACCUMULATED;
    }

    @Override
    public BigDecimal lineValue(StockLine line, Optional<BigDecimal> unitPrice) {
        return line.accumulatedValue();
    }

    @Override
    public BigDecimal storedValueAfterCredit(StockLine before, BigDecimal creditedValue) {
        return Money.of(before.accumulatedValue().add(creditedValue));
    }

    @Override
    public BigDecimal storedValueAfterDebit(StockLine before, long countedRemoved) {
        BigDecimal stored = before.accumulatedValue();
        return stored.subtract(Money.share(stored, countedRemoved, before.countedQuantity()));
    }
}
