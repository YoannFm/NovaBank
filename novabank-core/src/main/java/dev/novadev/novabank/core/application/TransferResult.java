package dev.novadev.novabank.core.application;

import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.api.operation.OperationStatus;
import dev.novadev.novabank.api.operation.OperationType;
import dev.novadev.novabank.core.money.Money;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Resultat d'un depot ou d'un retrait, pour l'affichage.
 *
 * @param status        issue
 * @param type          depot ou retrait
 * @param bank          banque concernee, si elle etait deja choisie
 * @param item          item concerne, s'il etait deja connu
 * @param quantity      unites deposees ou remises au joueur
 * @param undelivered   unites mises en livraison differee (depot refuse non rendu) ou non
 *                      remises lors d'un retrait (suivent la politique D13)
 * @param valueDelta    variation de valeur de la banque (negative pour un retrait)
 * @param bankValue     valeur de la banque apres l'operation, si connue
 * @param retryAfter    attente restante pour {@link TransferStatus#COOLDOWN}, sinon zero
 * @param ledgerStatus  statut de la banque pour {@link TransferStatus#REFUSED}
 */
public record TransferResult(TransferStatus status, OperationType type, Optional<EntityRef> bank,
                             Optional<ItemKey> item, long quantity, long undelivered, BigDecimal valueDelta,
                             Optional<BigDecimal> bankValue, Duration retryAfter,
                             Optional<OperationStatus> ledgerStatus) {

    /**
     * @throws NullPointerException si un champ est nul
     */
    public TransferResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(bank, "bank");
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(valueDelta, "valueDelta");
        Objects.requireNonNull(bankValue, "bankValue");
        Objects.requireNonNull(retryAfter, "retryAfter");
        Objects.requireNonNull(ledgerStatus, "ledgerStatus");
    }

    /**
     * @param type   operation
     * @param status motif du refus
     * @return un refus sans banque ni item
     */
    public static TransferResult refused(OperationType type, TransferStatus status) {
        return new TransferResult(status, type, Optional.empty(), Optional.empty(), 0L, 0L, Money.ZERO,
                Optional.empty(), Duration.ZERO, Optional.empty());
    }

    /**
     * @param type   operation
     * @param status motif du refus
     * @param bank   banque concernee
     * @param item   item concerne, ou {@code null}
     * @return le refus
     */
    public static TransferResult refused(OperationType type, TransferStatus status, EntityRef bank, ItemKey item) {
        return new TransferResult(status, type, Optional.of(bank), Optional.ofNullable(item), 0L, 0L, Money.ZERO,
                Optional.empty(), Duration.ZERO, Optional.empty());
    }

    /**
     * @param type      operation
     * @param remaining attente restante
     * @return le refus pour delai non ecoule
     */
    public static TransferResult cooldown(OperationType type, Duration remaining) {
        return new TransferResult(TransferStatus.COOLDOWN, type, Optional.empty(), Optional.empty(), 0L, 0L,
                Money.ZERO, Optional.empty(), remaining, Optional.empty());
    }

    /**
     * Refus de la banque : les statuts connus sont traduits, les autres deviennent
     * {@link TransferStatus#REFUSED}.
     *
     * @param type        operation
     * @param bank        banque
     * @param item        item
     * @param status      statut de la banque
     * @param undelivered unites mises en livraison differee
     * @return le refus
     */
    public static TransferResult ledgerRefusal(OperationType type, EntityRef bank, ItemKey item,
                                               OperationStatus status, long undelivered) {
        TransferStatus translated = switch (status) {
            case INSUFFICIENT_STOCK -> TransferStatus.INSUFFICIENT_STOCK;
            case NOT_ACCEPTED -> TransferStatus.NOT_ACCEPTED;
            default -> TransferStatus.REFUSED;
        };
        return new TransferResult(translated, type, Optional.of(bank), Optional.of(item), 0L, undelivered, Money.ZERO,
                Optional.empty(), Duration.ZERO, Optional.of(status));
    }

    /**
     * @param status      issue
     * @param type        operation
     * @param bank        banque
     * @param item        item
     * @param quantity    unites deplacees
     * @param undelivered unites non remises ou en livraison differee
     * @param valueDelta  variation de valeur
     * @param bankValue   valeur apres l'operation, ou {@code null} si inconnue
     * @return le resultat
     */
    public static TransferResult moved(TransferStatus status, OperationType type, EntityRef bank, ItemKey item,
                                       long quantity, long undelivered, BigDecimal valueDelta, BigDecimal bankValue) {
        return new TransferResult(status, type, Optional.of(bank), Optional.of(item), quantity, undelivered,
                Money.of(valueDelta), Optional.ofNullable(bankValue), Duration.ZERO, Optional.empty());
    }
}
