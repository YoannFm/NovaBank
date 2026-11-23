package dev.novadev.novabank.core.bank;

import dev.novadev.novabank.core.money.Money;
import dev.novadev.novabank.core.text.ConsoleText;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Stock d'un item dans une banque. Valeur immuable.
 *
 * <p>Seules les unites au-dessus de la base sont <em>comptees</em> (elles portent de
 * la valeur) : {@code comptees = max(0, quantity - baselineQuantity)}. La base est
 * fixee par une reinitialisation et ne baisse jamais ensuite, meme quand le stock
 * passe dessous : retirer puis redeposer des blocs presents au reset ne regenere
 * donc aucune valeur. Toute la logique d'unites comptees est ici, commune aux modes
 * de valorisation.
 *
 * @param quantity         nombre d'unites en stock, positif ou nul
 * @param baselineQuantity nombre d'unites non comptees issu du dernier reset
 * @param accumulatedValue valeur des unites comptees (mode cumule uniquement, sinon zero)
 */
public record StockLine(long quantity, long baselineQuantity, BigDecimal accumulatedValue) {

    /** Ligne vide. */
    public static final StockLine EMPTY = new StockLine(0L, 0L, Money.ZERO);

    /**
     * Construit une ligne en validant ses invariants.
     *
     * @throws IllegalArgumentException si une quantite ou la valeur est negative
     */
    public StockLine {
        Objects.requireNonNull(accumulatedValue, "accumulatedValue");
        if (quantity < 0 || baselineQuantity < 0) {
            throw new IllegalArgumentException(ConsoleText.tr("Quantites negatives : {0} / {1}",
                    quantity, baselineQuantity));
        }
        if (accumulatedValue.signum() < 0) {
            throw new IllegalArgumentException(ConsoleText.tr("Valeur cumulee negative : {0}", accumulatedValue));
        }
        accumulatedValue = Money.of(accumulatedValue);
    }

    /**
     * Nombre d'unites comptees.
     *
     * @return {@code max(0, quantity - baselineQuantity)}
     */
    public long countedQuantity() {
        return Math.max(0L, quantity - baselineQuantity);
    }

    /**
     * Ajoute des unites au-dessus du stock ; elles ne sont comptees qu'au-dela de la base.
     *
     * @param units nombre d'unites, positif ou nul
     * @return la nouvelle ligne
     * @throws ArithmeticException si le stock deborde
     */
    public StockLine plus(long units) {
        requireNonNegative(units);
        return new StockLine(Math.addExact(quantity, units), baselineQuantity, accumulatedValue);
    }

    /**
     * Ajoute des unites qui ne doivent jamais etre comptees : elles comblent d'abord le
     * deficit sous la base, puis la base monte d'autant pour le surplus.
     *
     * @param units nombre d'unites, positif ou nul
     * @return la nouvelle ligne, avec le meme nombre d'unites comptees
     * @throws ArithmeticException si le stock deborde
     */
    public StockLine plusUncounted(long units) {
        requireNonNegative(units);
        long deficit = Math.max(0L, baselineQuantity - quantity);
        long baselineRaise = Math.max(0L, units - deficit);
        return new StockLine(Math.addExact(quantity, units), Math.addExact(baselineQuantity, baselineRaise),
                accumulatedValue);
    }

    /**
     * Retire des unites ; les unites comptees partent en premier. La base ne baisse jamais.
     *
     * @param units nombre d'unites, entre 0 et {@link #quantity()}
     * @return la nouvelle ligne
     * @throws IllegalArgumentException si le stock est insuffisant
     */
    public StockLine minus(long units) {
        requireNonNegative(units);
        if (units > quantity) {
            throw new IllegalArgumentException(ConsoleText.tr("Stock insuffisant : {0} > {1}", units, quantity));
        }
        return new StockLine(quantity - units, baselineQuantity, accumulatedValue);
    }

    /**
     * Reinitialise la valeur : tout le stock devient non compte, le stock ne change pas.
     *
     * @return la ligne reinitialisee
     */
    public StockLine reset() {
        return new StockLine(quantity, quantity, Money.ZERO);
    }

    /**
     * Remplace la valeur cumulee.
     *
     * @param value nouvelle valeur, positive ou nulle
     * @return la nouvelle ligne
     */
    public StockLine withAccumulatedValue(BigDecimal value) {
        return new StockLine(quantity, baselineQuantity, value);
    }

    /**
     * Indique si la ligne ne porte plus aucune information (ni stock, ni base, ni valeur).
     *
     * @return {@code true} si la ligne peut etre oubliee
     */
    public boolean isBlank() {
        return quantity == 0L && baselineQuantity == 0L && accumulatedValue.signum() == 0;
    }

    private static void requireNonNegative(long units) {
        if (units < 0) {
            throw new IllegalArgumentException(ConsoleText.tr("Nombre d'unites negatif : {0}", units));
        }
    }
}
