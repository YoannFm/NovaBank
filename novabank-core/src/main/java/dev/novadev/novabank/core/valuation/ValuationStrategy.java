package dev.novadev.novabank.core.valuation;

import dev.novadev.novabank.api.valuation.ValuationMode;
import dev.novadev.novabank.core.bank.StockLine;
import java.math.BigDecimal;
import java.util.Optional;

/**
 * Regle de valeur d'une ligne de stock pour un {@link ValuationMode}.
 *
 * <p>La strategie ne connait pas les unites comptees (calculees par {@link StockLine})
 * : elle dit seulement ce que vaut une ligne et comment evolue la valeur conservee
 * quand des unites comptees entrent ou sortent.
 */
public interface ValuationStrategy {

    /**
     * Mode implemente.
     *
     * @return le mode
     */
    ValuationMode mode();

    /**
     * Valeur courante d'une ligne, jamais negative.
     *
     * @param line      ligne a valoriser
     * @param unitPrice prix courant de l'item, vide s'il n'est plus au catalogue
     * @return la valeur de la ligne
     */
    BigDecimal lineValue(StockLine line, Optional<BigDecimal> unitPrice);

    /**
     * Valeur conservee par la ligne apres l'entree d'unites comptees.
     *
     * @param before        ligne avant le mouvement
     * @param creditedValue valeur des unites comptees entrantes
     * @return la nouvelle valeur conservee
     */
    BigDecimal storedValueAfterCredit(StockLine before, BigDecimal creditedValue);

    /**
     * Valeur conservee par la ligne apres la sortie d'unites comptees.
     *
     * @param before         ligne avant le mouvement
     * @param countedRemoved unites comptees sortantes, au plus {@link StockLine#countedQuantity()}
     * @return la nouvelle valeur conservee
     */
    BigDecimal storedValueAfterDebit(StockLine before, long countedRemoved);

    /**
     * Strategie correspondant a un mode.
     *
     * @param mode mode de valorisation
     * @return la strategie (sans etat)
     */
    static ValuationStrategy of(ValuationMode mode) {
        return switch (mode) {
            case DERIVED -> new DerivedValuation();
            case ACCUMULATED -> new AccumulatedValuation();
        };
    }
}
