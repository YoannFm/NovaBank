package dev.novadev.novabank.core.ledger;

import dev.novadev.novabank.api.OperationId;
import dev.novadev.novabank.api.operation.OperationResult;
import dev.novadev.novabank.api.operation.OperationStatus;
import dev.novadev.novabank.api.operation.OperationType;
import dev.novadev.novabank.core.bank.Bank;
import dev.novadev.novabank.core.delivery.PendingDelivery;
import dev.novadev.novabank.core.delivery.PendingDeliveryStore;
import dev.novadev.novabank.core.delivery.UndeliveredPolicy;
import dev.novadev.novabank.core.journal.OperationRecord;
import dev.novadev.novabank.core.journal.OperationState;
import dev.novadev.novabank.core.money.Money;
import dev.novadev.novabank.core.repository.ApplyOutcome;
import dev.novadev.novabank.core.repository.BankRepository;
import dev.novadev.novabank.core.repository.MutationDecision;
import dev.novadev.novabank.core.valuation.Movement;
import dev.novadev.novabank.core.valuation.StockAccounting;
import java.util.Optional;

/**
 * Seconde etape d'un retrait : confirmation (livraison) ou annulation (rendu).
 *
 * <p>Les unites livrees sont reputees etre les unites comptees du retrait ; les unites
 * non livrees rendues a la banque sont donc d'abord les non comptees. Si la banque a ete
 * reinitialisee depuis la reservation, tout est rendu non compte et sans valeur : un
 * reset efface aussi la valeur des unites alors en cours de retrait. La livraison en
 * attente est cle par l'identifiant du retrait : un rejeu de confirmation la
 * re-enregistre sans la dupliquer, ce qui couvre aussi un arret entre l'ecriture du
 * journal et celle de la file.
 */
final class WithdrawalSettlement {

    private final BankRepository repository;
    private final StockAccounting accounting;
    private final PendingDeliveryStore deliveries;
    private final UndeliveredPolicy policy;
    private final OperationResults results;

    WithdrawalSettlement(BankRepository repository, StockAccounting accounting, PendingDeliveryStore deliveries,
                         UndeliveredPolicy policy, OperationResults results) {
        this.repository = repository;
        this.accounting = accounting;
        this.deliveries = deliveries;
        this.policy = policy;
        this.results = results;
    }

    OperationResult confirm(OperationId id, long deliveredQuantity) {
        Optional<OperationRecord> reservation = findWithdrawal(id);
        if (reservation.isEmpty()) {
            return OperationResult.rejected(id, OperationStatus.UNKNOWN_OPERATION);
        }
        if (deliveredQuantity < 0 || deliveredQuantity > reservation.get().quantity()) {
            return OperationResult.rejected(id, OperationStatus.INVALID_QUANTITY);
        }
        ApplyOutcome outcome = repository.settle(id, (bank, record) -> switch (record.state()) {
            case RESERVED -> OverflowGuard.decide(() -> commit(bank, record, deliveredQuantity));
            case COMMITTED -> MutationDecision.alreadyApplied();
            case RETURNED -> MutationDecision.reject(OperationStatus.ALREADY_SETTLED);
        });
        queueUndelivered(outcome);
        return results.ofSettlement(id, outcome);
    }

    OperationResult cancel(OperationId id) {
        if (findWithdrawal(id).isEmpty()) {
            return OperationResult.rejected(id, OperationStatus.UNKNOWN_OPERATION);
        }
        ApplyOutcome outcome = repository.settle(id, (bank, record) -> switch (record.state()) {
            case RESERVED -> OverflowGuard.decide(() -> giveBack(bank, record, 0L, OperationState.RETURNED));
            case RETURNED -> MutationDecision.alreadyApplied();
            case COMMITTED -> MutationDecision.reject(OperationStatus.ALREADY_SETTLED);
        });
        return results.ofSettlement(id, outcome);
    }

    private MutationDecision commit(Bank bank, OperationRecord record, long delivered) {
        if (delivered == record.quantity() || policy == UndeliveredPolicy.PENDING_DELIVERY) {
            return MutationDecision.accept(bank, record.settle(OperationState.COMMITTED, delivered, 0L, Money.ZERO));
        }
        return giveBack(bank, record, delivered, OperationState.COMMITTED);
    }

    private MutationDecision giveBack(Bank bank, OperationRecord record, long delivered, OperationState newState) {
        long returned = record.quantity() - delivered;
        boolean resetSinceReservation = bank.resetEpoch() > record.withdrawal().reservedEpoch();
        long returnedCounted = resetSinceReservation ? 0L : Math.max(0L, record.countedQuantity() - delivered);
        Movement movement = accounting.restore(bank, record.itemKey(), returned, returnedCounted,
                Money.share(record.valueDelta().negate(), returnedCounted, record.countedQuantity()));
        return MutationDecision.accept(movement.bank(),
                record.settle(newState, delivered, returned, Money.atLeastZero(movement.valueDelta())));
    }

    private void queueUndelivered(ApplyOutcome outcome) {
        OperationRecord record;
        if (outcome instanceof ApplyOutcome.Applied applied) {
            record = applied.record();
        } else if (outcome instanceof ApplyOutcome.Duplicate duplicate) {
            record = duplicate.record();
        } else {
            return;
        }
        long undelivered = record.undeliveredQuantity();
        if (undelivered > 0) {
            deliveries.add(new PendingDelivery(record.operationId(), record.withdrawal().recipient(),
                    record.itemKey(), undelivered));
        }
    }

    private Optional<OperationRecord> findWithdrawal(OperationId id) {
        return repository.findOperation(id).filter(record -> record.type() == OperationType.WITHDRAW);
    }
}
