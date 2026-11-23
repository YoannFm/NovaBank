package dev.novadev.novabank.core.application;

import dev.novadev.novabank.api.OperationId;
import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.api.operation.OperationResult;
import dev.novadev.novabank.api.operation.OperationType;
import java.math.BigDecimal;

/**
 * Items d'un type retires de l'inventaire pour un depot, et leur devenir : valides en
 * base, a rendre au joueur, ou d'issue inconnue (ni rendus, ni comptes).
 */
final class DepositLine {

    private enum State {
        /** Retire de l'inventaire, pas encore soumis a la banque. */
        TAKEN,
        /** Valide en base (ou deja valide : rejeu). */
        RECORDED,
        /** Refuse ou non enregistre : a rendre au joueur. */
        REFUND,
        /** Issue inconnue : ni rendu, ni compte. */
        UNKNOWN
    }

    private static final OperationType TYPE = OperationType.DEPOSIT;

    private final OperationId id;
    private final ItemKey key;
    private final long quantity;
    private State state = State.TAKEN;
    private OperationResult result;
    private BigDecimal recordedValue = BigDecimal.ZERO;
    private long undelivered;

    DepositLine(OperationId id, ItemKey key, long quantity) {
        this.id = id;
        this.key = key;
        this.quantity = quantity;
    }

    OperationId id() {
        return id;
    }

    ItemKey key() {
        return key;
    }

    long quantity() {
        return quantity;
    }

    /** Reponse de la banque : valide ou refuse (items a rendre). */
    void settled(OperationResult response) {
        result = response;
        state = response.isEffective() ? State.RECORDED : State.REFUND;
    }

    /** Validation constatee dans le journal apres une reponse perdue. */
    void recorded(BigDecimal valueDelta) {
        state = State.RECORDED;
        recordedValue = valueDelta;
    }

    /** Absence constatee dans le journal : items a rendre. */
    void toRefund() {
        state = State.REFUND;
    }

    /** Base illisible : issue inconnue. */
    void unknown() {
        state = State.UNKNOWN;
    }

    /** Items rendus au joueur, dont {@code leftover} en livraison differee. */
    void returned(long leftover) {
        state = State.REFUND;
        undelivered = leftover;
    }

    boolean isRecorded() {
        return state == State.RECORDED;
    }

    boolean isPending() {
        return state == State.TAKEN;
    }

    boolean mustBeReturned() {
        return state == State.REFUND || state == State.TAKEN;
    }

    TransferResult toResult(EntityRef bank) {
        return switch (state) {
            case RECORDED -> TransferResult.moved(TransferStatus.SUCCESS, TYPE, bank, key, quantity, 0L,
                    result != null ? result.valueDelta() : recordedValue,
                    result != null ? result.bankValue().orElse(null) : null);
            case REFUND -> result != null
                    ? TransferResult.ledgerRefusal(TYPE, bank, key, result.status(), undelivered)
                    : TransferResult.moved(TransferStatus.STORAGE_ERROR, TYPE, bank, key, 0L, undelivered,
                            BigDecimal.ZERO, null);
            case TAKEN, UNKNOWN -> TransferResult.moved(TransferStatus.INTERNAL_ERROR, TYPE, bank, key, 0L, 0L,
                    BigDecimal.ZERO, null);
        };
    }
}
