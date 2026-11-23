package dev.novadev.novabank.core.journal;

import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.api.OperationId;
import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.api.operation.OperationType;
import dev.novadev.novabank.core.money.Money;
import dev.novadev.novabank.core.text.ConsoleText;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entree du journal d'operations. Valeur immuable.
 *
 * <p>{@code quantity}, {@code countedQuantity} et {@code valueDelta} decrivent le
 * mouvement initial (depot, reservation de retrait ou reset) et ne changent jamais :
 * c'est sur eux que s'appuie la restitution exacte d'un retrait. Le devenir d'un
 * retrait est suivi a part dans {@link WithdrawalProgress}.
 *
 * @param operationId     identifiant de l'operation
 * @param type            nature de l'operation
 * @param bankId          banque concernee
 * @param itemKey         item deplace ; {@code null} uniquement pour un reset
 * @param quantity        unites deplacees (stock total pour un reset)
 * @param countedQuantity parmi elles, unites comptees impactees
 * @param valueDelta      variation de valeur de la banque, negative pour une sortie
 * @param state           etat de l'operation
 * @param timestamp       horodatage du mouvement initial
 * @param withdrawal      suivi du retrait ; {@code null} sauf pour un retrait
 */
public record OperationRecord(OperationId operationId, OperationType type, BankId bankId, ItemKey itemKey,
                              long quantity, long countedQuantity, BigDecimal valueDelta, OperationState state,
                              Instant timestamp, WithdrawalProgress withdrawal) {

    /**
     * Construit une entree en verifiant sa coherence avec son type.
     *
     * @throws IllegalArgumentException si l'entree est incoherente
     */
    public OperationRecord {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(bankId, "bankId");
        Objects.requireNonNull(valueDelta, "valueDelta");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(timestamp, "timestamp");
        valueDelta = Money.of(valueDelta);
        require(quantity >= 0 && countedQuantity >= 0 && countedQuantity <= quantity, "quantites incoherentes");
        require((itemKey == null) == (type == OperationType.RESET), "item obligatoire sauf pour un reset");
        require((withdrawal == null) == (type != OperationType.WITHDRAW), "suivi present pour un retrait seulement");
        if (withdrawal == null) {
            require(state == OperationState.COMMITTED, "seul un retrait a un etat intermediaire");
        } else {
            requireConsistentWithdrawal(quantity, state, withdrawal);
        }
    }

    /**
     * Entree d'un depot.
     *
     * @param id              operation
     * @param bankId          banque
     * @param item            item depose
     * @param quantity        unites deposees
     * @param countedQuantity unites comptees ajoutees
     * @param valueDelta      valeur ajoutee
     * @param at              horodatage
     * @return l'entree {@link OperationState#COMMITTED}
     */
    public static OperationRecord deposit(OperationId id, BankId bankId, ItemKey item, long quantity,
                                          long countedQuantity, BigDecimal valueDelta, Instant at) {
        return new OperationRecord(id, OperationType.DEPOSIT, bankId, item, quantity, countedQuantity,
                valueDelta, OperationState.COMMITTED, at, null);
    }

    /**
     * Entree d'un retrait reserve.
     *
     * @param id              operation
     * @param bankId          banque
     * @param item            item retire
     * @param quantity        unites reservees
     * @param countedQuantity unites comptees retirees
     * @param valueDelta      valeur retiree (negative ou nulle)
     * @param at              horodatage
     * @param recipient       joueur destinataire
     * @param reservedEpoch   epoch de reset de la banque a la reservation
     * @return l'entree {@link OperationState#RESERVED}
     */
    public static OperationRecord reservation(OperationId id, BankId bankId, ItemKey item, long quantity,
                                              long countedQuantity, BigDecimal valueDelta, Instant at,
                                              UUID recipient, long reservedEpoch) {
        return new OperationRecord(id, OperationType.WITHDRAW, bankId, item, quantity, countedQuantity,
                valueDelta, OperationState.RESERVED, at, WithdrawalProgress.reserved(recipient, reservedEpoch));
    }

    /**
     * Entree d'un reset.
     *
     * @param id              operation
     * @param bankId          banque
     * @param quantity        stock total de la banque (inchange)
     * @param countedQuantity unites comptees effacees
     * @param valueDelta      valeur effacee (negative ou nulle)
     * @param at              horodatage
     * @return l'entree {@link OperationState#COMMITTED}
     */
    public static OperationRecord reset(OperationId id, BankId bankId, long quantity, long countedQuantity,
                                        BigDecimal valueDelta, Instant at) {
        return new OperationRecord(id, OperationType.RESET, bankId, null, quantity, countedQuantity,
                valueDelta, OperationState.COMMITTED, at, null);
    }

    /**
     * Solde un retrait reserve.
     *
     * @param newState      {@link OperationState#COMMITTED} ou {@link OperationState#RETURNED}
     * @param delivered     unites livrees
     * @param returned      unites rendues a la banque
     * @param returnedValue valeur re-creditee
     * @return l'entree soldee
     * @throws IllegalStateException si l'entree n'est pas un retrait reserve
     */
    public OperationRecord settle(OperationState newState, long delivered, long returned, BigDecimal returnedValue) {
        if (state != OperationState.RESERVED) {
            throw new IllegalStateException(ConsoleText.tr("Retrait non reserve : {0} ({1})", operationId, state));
        }
        return new OperationRecord(operationId, type, bankId, itemKey, quantity, countedQuantity, valueDelta,
                newState, timestamp,
                new WithdrawalProgress(withdrawal.recipient(), withdrawal.reservedEpoch(), delivered, returned,
                        returnedValue));
    }

    /**
     * Unites d'un retrait confirme ni livrees ni rendues, donc en attente de livraison.
     *
     * @return le reste a livrer, zero hors retrait confirme
     */
    public long undeliveredQuantity() {
        if (withdrawal == null || state != OperationState.COMMITTED) {
            return 0L;
        }
        return quantity - withdrawal.deliveredQuantity() - withdrawal.returnedQuantity();
    }

    private static void requireConsistentWithdrawal(long quantity, OperationState state, WithdrawalProgress progress) {
        long settled = Math.addExact(progress.deliveredQuantity(), progress.returnedQuantity());
        require(settled <= quantity, "plus d'unites soldees que reservees");
        switch (state) {
            case RESERVED -> require(settled == 0L, "retrait reserve deja solde");
            case RETURNED -> require(progress.returnedQuantity() == quantity, "retrait rendu incompletement");
            case COMMITTED -> {
                // Toute repartition livree / rendue / en attente est valide.
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(ConsoleText.tr("Entree de journal invalide : {0}", message));
        }
    }
}
