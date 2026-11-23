package dev.novadev.novabank.core.money;

import dev.novadev.novabank.core.text.ConsoleText;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Seul endroit qui fixe l'echelle et l'arrondi des valeurs monetaires.
 *
 * <p>Toute valeur produite par le coeur passe par ces methodes : {@value #SCALE}
 * decimales, arrondi bancaire ({@link RoundingMode#HALF_EVEN}). Les montants sont
 * toujours des {@link BigDecimal}, jamais des {@code double}.
 */
public final class Money {

    /** Nombre de decimales conservees. */
    public static final int SCALE = 4;

    /** Mode d'arrondi applique a chaque calcul. */
    public static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

    /** Zero a l'echelle monetaire. */
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE, ROUNDING);

    private Money() {
    }

    /**
     * Ramene un montant a l'echelle monetaire.
     *
     * @param amount montant quelconque
     * @return le montant arrondi a {@value #SCALE} decimales
     */
    public static BigDecimal of(BigDecimal amount) {
        return Objects.requireNonNull(amount, "amount").setScale(SCALE, ROUNDING);
    }

    /**
     * Valeur de {@code quantity} unites au prix unitaire {@code unitPrice}.
     *
     * @param unitPrice prix d'une unite
     * @param quantity  nombre d'unites
     * @return le produit arrondi une seule fois
     */
    public static BigDecimal times(BigDecimal unitPrice, long quantity) {
        return of(unitPrice.multiply(BigDecimal.valueOf(quantity)));
    }

    /**
     * Part {@code part / whole} d'un montant, arrondie une seule fois.
     *
     * <p>Si {@code part == whole}, le montant est rendu tel quel : prendre la totalite
     * ne laisse jamais de residu d'arrondi.
     *
     * @param total montant a partager
     * @param part  numerateur, entre 0 et {@code whole}
     * @param whole denominateur, positif sauf si {@code part == whole}
     * @return la part du montant
     * @throws IllegalArgumentException si {@code part} est hors de {@code [0, whole]}
     */
    public static BigDecimal share(BigDecimal total, long part, long whole) {
        if (part < 0 || part > whole) {
            throw new IllegalArgumentException(ConsoleText.tr("Part {0} hors de [0, {1}]", part, whole));
        }
        if (part == whole) {
            return of(total);
        }
        return total.multiply(BigDecimal.valueOf(part)).divide(BigDecimal.valueOf(whole), SCALE, ROUNDING);
    }

    /**
     * Borne un montant a zero par le bas.
     *
     * @param amount montant quelconque
     * @return {@code amount} s'il est positif ou nul, sinon {@link #ZERO}
     */
    public static BigDecimal atLeastZero(BigDecimal amount) {
        return amount.signum() < 0 ? ZERO : of(amount);
    }
}
