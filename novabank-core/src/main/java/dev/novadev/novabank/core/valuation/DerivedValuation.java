package dev.novadev.novabank.core.valuation;

import dev.novadev.novabank.api.valuation.ValuationMode;
import dev.novadev.novabank.core.bank.StockLine;
import dev.novadev.novabank.core.money.Money;
import java.math.BigDecimal;
import java.util.Optional;

/**
 * Mode {@link ValuationMode#DERIVED} : valeur = unites comptees x prix courant.
 * Rien n'est conserve dans la ligne, un changement de prix se repercute aussitot.
 */
final class DerivedValuation implements ValuationStrategy {

    @Override
    public ValuationMode mode() {
        return ValuationMode.DERIVED;
    }

    @Override
    public BigDecimal lineValue(StockLine line, Optional<BigDecimal> unitPrice) {
        return unitPrice.map(price -> Money.times(price, line.countedQuantity())).orElse(Money.ZERO);
    }

    @Override
    public BigDecimal storedValueAfterCredit(StockLine before, BigDecimal creditedValue) {
        return Money.ZERO;
    }

    @Override
    public BigDecimal storedValueAfterDebit(StockLine before, long countedRemoved) {
        return Money.ZERO;
    }
}
