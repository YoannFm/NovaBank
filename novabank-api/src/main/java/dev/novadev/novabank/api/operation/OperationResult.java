package dev.novadev.novabank.api.operation;

import dev.novadev.novabank.api.OperationId;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * Resultat immuable d'une operation bancaire.
 *
 * <p>Pour une operation appliquee ({@link OperationStatus#SUCCESS}) ou rejouee
 * ({@link OperationStatus#DUPLICATE_OPERATION}) :
 * <ul>
 *   <li>{@link #quantity()} est la quantite effectivement deplacee par l'etape :
 *       deposee, reservee, livree (confirmation) ou rendue a la banque (annulation) ;</li>
 *   <li>{@link #valueDelta()} est la variation de valeur de la banque causee par
 *       l'etape, negative pour un retrait ;</li>
 *   <li>{@link #bankValue()} est la valeur courante de la banque apres l'operation.</li>
 * </ul>
 * Pour un rejeu, quantite et variation decrivent l'application d'origine. Pour un
 * refus, la quantite et la variation valent zero et la valeur de banque est absente.
 */
public final class OperationResult {

    private final OperationStatus status;
    private final OperationId operationId;
    private final long quantity;
    private final BigDecimal valueDelta;
    private final BigDecimal bankValue;

    private OperationResult(OperationStatus status, OperationId operationId, long quantity,
                            BigDecimal valueDelta, BigDecimal bankValue) {
        this.status = Objects.requireNonNull(status, "status");
        this.operationId = Objects.requireNonNull(operationId, "operationId");
        this.quantity = quantity;
        this.valueDelta = Objects.requireNonNull(valueDelta, "valueDelta");
        this.bankValue = bankValue;
    }

    /**
     * Resultat d'une operation qui vient d'etre appliquee.
     *
     * @param operationId identifiant de l'operation
     * @param quantity    quantite deplacee par l'etape
     * @param valueDelta  variation de valeur de la banque
     * @param bankValue   valeur de la banque apres l'operation
     * @return le resultat {@link OperationStatus#SUCCESS}
     */
    public static OperationResult success(OperationId operationId, long quantity,
                                          BigDecimal valueDelta, BigDecimal bankValue) {
        return new OperationResult(OperationStatus.SUCCESS, operationId, quantity, valueDelta,
                Objects.requireNonNull(bankValue, "bankValue"));
    }

    /**
     * Resultat d'une operation deja appliquee auparavant (rejeu sans effet).
     *
     * @param operationId identifiant de l'operation
     * @param quantity    quantite deplacee par l'application d'origine
     * @param valueDelta  variation de valeur causee par l'application d'origine
     * @param bankValue   valeur courante de la banque
     * @return le resultat {@link OperationStatus#DUPLICATE_OPERATION}
     */
    public static OperationResult duplicate(OperationId operationId, long quantity,
                                            BigDecimal valueDelta, BigDecimal bankValue) {
        return new OperationResult(OperationStatus.DUPLICATE_OPERATION, operationId, quantity, valueDelta,
                Objects.requireNonNull(bankValue, "bankValue"));
    }

    /**
     * Resultat d'une operation refusee, sans aucun effet.
     *
     * @param operationId identifiant de l'operation
     * @param status      motif du refus
     * @return le resultat de refus
     * @throws IllegalArgumentException si {@code status} n'est pas un statut de refus
     */
    public static OperationResult rejected(OperationId operationId, OperationStatus status) {
        if (status == OperationStatus.SUCCESS || status == OperationStatus.DUPLICATE_OPERATION) {
            throw new IllegalArgumentException("Refusal status expected: " + status);
        }
        return new OperationResult(status, operationId, 0L, BigDecimal.ZERO, null);
    }

    /**
     * Issue de l'operation.
     *
     * @return le statut
     */
    public OperationStatus status() {
        return status;
    }

    /**
     * Identifiant de l'operation concernee.
     *
     * @return l'identifiant
     */
    public OperationId operationId() {
        return operationId;
    }

    /**
     * Indique si l'operation vient d'etre appliquee.
     *
     * @return {@code true} pour {@link OperationStatus#SUCCESS}
     */
    public boolean isSuccess() {
        return status == OperationStatus.SUCCESS;
    }

    /**
     * Indique si l'effet de l'operation est acquis, maintenant ou lors d'une
     * tentative precedente.
     *
     * @return {@code true} pour {@link OperationStatus#SUCCESS} et
     *         {@link OperationStatus#DUPLICATE_OPERATION}
     */
    public boolean isEffective() {
        return isSuccess() || status == OperationStatus.DUPLICATE_OPERATION;
    }

    /**
     * Quantite deplacee par l'etape (zero en cas de refus).
     *
     * @return la quantite
     */
    public long quantity() {
        return quantity;
    }

    /**
     * Variation de valeur de la banque causee par l'etape (zero en cas de refus).
     *
     * @return la variation, negative pour un retrait
     */
    public BigDecimal valueDelta() {
        return valueDelta;
    }

    /**
     * Valeur de la banque apres l'operation, absente en cas de refus.
     *
     * @return la valeur, ou vide
     */
    public Optional<BigDecimal> bankValue() {
        return Optional.ofNullable(bankValue);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof OperationResult that)) {
            return false;
        }
        return quantity == that.quantity
                && status == that.status
                && operationId.equals(that.operationId)
                && valueDelta.compareTo(that.valueDelta) == 0
                && (bankValue == null ? that.bankValue == null
                        : that.bankValue != null && bankValue.compareTo(that.bankValue) == 0);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, operationId, quantity);
    }

    @Override
    public String toString() {
        return "OperationResult[status=" + status + ", operationId=" + operationId + ", quantity=" + quantity
                + ", valueDelta=" + valueDelta.toPlainString()
                + ", bankValue=" + (bankValue == null ? "-" : bankValue.toPlainString()) + ']';
    }
}
