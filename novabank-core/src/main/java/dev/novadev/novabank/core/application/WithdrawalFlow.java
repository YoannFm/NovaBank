package dev.novadev.novabank.core.application;

import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.api.OperationId;
import dev.novadev.novabank.api.entity.EntityMembership;
import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.api.operation.OperationResult;
import dev.novadev.novabank.api.operation.OperationType;
import dev.novadev.novabank.core.access.Permissions;
import dev.novadev.novabank.core.bank.Bank;
import dev.novadev.novabank.core.text.ConsoleText;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Retrait en deux temps.
 *
 * <ol>
 *   <li>thread joueur : permission, appartenances ;</li>
 *   <li>IO : banque active, droit du role, quantite, {@code reserveWithdrawal(opId)} (stock
 *       et valeur debites en base) ;</li>
 *   <li>thread joueur : remise des items, mesure de la quantite effectivement donnee
 *       (joueur parti : 0) ;</li>
 *   <li>IO : {@code confirmWithdrawal(opId, livre)} ; le reste suit la politique D13 ;</li>
 *   <li>thread joueur : sauvegarde des donnees du joueur.</li>
 * </ol>
 *
 * <p>Confirmation impossible apres toutes les tentatives : le retrait reste RESERVED et les
 * donnees du joueur ne sont PAS sauvegardees. Il sera solde au prochain demarrage avec
 * {@code livre = 0} (reconciliation) : sans crash, le joueur garde les items remis et le
 * reste suit la politique D13 a tort (risque residuel journalise en SEVERE avec
 * l'identifiant, a verifier manuellement) ; avec un crash, l'inventaire revient a sa
 * derniere sauvegarde, sans les items, et la reconciliation est exacte.
 */
final class WithdrawalFlow extends Flow<TransferResult> {

    private static final OperationType TYPE = OperationType.WITHDRAW;

    private final ItemKey item;
    private final Amount amount;
    private final TransferRules rules;
    private final OperationId operationId = OperationId.random();
    private EntityRef bank;
    private OperationResult reservation;

    WithdrawalFlow(OperationPorts ports, UUID playerId, ItemKey item, Amount amount, TransferRules rules,
                   Consumer<TransferResult> done, Runnable release) {
        super(ports, playerId, done, release);
        this.item = item;
        this.amount = amount;
        this.rules = rules;
    }

    void start() {
        onPlayer(this::checkAccess, () -> finish(TransferResult.refused(TYPE, interruptedStatus())));
    }

    private void checkAccess() {
        if (!ports.permissions().has(playerId, Permissions.WITHDRAW)) {
            finish(TransferResult.refused(TYPE, TransferStatus.NO_PERMISSION));
            return;
        }
        List<EntityMembership> memberships = ports.ownership().memberships(playerId, rules.enabledTypes());
        Map<EntityRef, Map<OperationType, Boolean>> hostRights = ports.ownership().rights(playerId, memberships);
        onIo(() -> reserve(memberships, hostRights));
    }

    private void reserve(List<EntityMembership> memberships, Map<EntityRef, Map<OperationType, Boolean>> hostRights) {
        Optional<EntityMembership> active = BankResolution.active(ports, playerId, memberships, rules);
        if (active.isEmpty()) {
            finish(TransferResult.refused(TYPE, TransferStatus.NO_BANK));
            return;
        }
        bank = active.get().entity();
        if (!BankResolution.allowed(rules, hostRights, active.get(), TYPE)) {
            finish(TransferResult.refused(TYPE, TransferStatus.ROLE_NOT_ALLOWED, bank, item));
            return;
        }
        if (!amount.all() && amount.value() > rules.maxQuantityPerOperation()) {
            finish(TransferResult.refused(TYPE, TransferStatus.QUANTITY_LIMIT, bank, item));
            return;
        }
        BankId bankId = BankId.forOwner(bank);
        long stock = stockOf(ports.banks().findBank(bankId), item);
        long quantity = amount.resolve(stock, rules.maxQuantityPerOperation());
        if (quantity <= 0 || quantity > stock) {
            finish(TransferResult.refused(TYPE, TransferStatus.INSUFFICIENT_STOCK, bank, item));
            return;
        }
        ports.actors().remember(operationId, playerId);
        Optional<OperationResult> reserved = ports.retry().call("reservation du retrait " + operationId,
                () -> ports.ledger().reserveWithdrawal(operationId, bankId, item, quantity, playerId));
        if (reserved.isEmpty()) {
            // Rien n'a ete remis : une reservation enregistree malgre l'erreur est annulee
            // (ou, a defaut, soldee au prochain demarrage avec livre = 0).
            ports.retry().call("annulation du retrait " + operationId,
                    () -> ports.ledger().cancelWithdrawal(operationId));
            finish(TransferResult.refused(TYPE, TransferStatus.STORAGE_ERROR, bank, item));
            return;
        }
        if (!reserved.get().isEffective()) {
            finish(TransferResult.ledgerRefusal(TYPE, bank, item, reserved.get().status(), 0L));
            return;
        }
        reservation = reserved.get();
        onPlayer(this::give, () -> onIo(() -> confirm(0L)));
    }

    private void give() {
        long quantity = reservation.quantity();
        long leftover = ports.inventory().give(playerId, item, quantity);
        long delivered = quantity - Math.max(0L, Math.min(leftover, quantity));
        onIo(() -> confirm(delivered));
    }

    private void confirm(long delivered) {
        Optional<OperationResult> confirmed = ports.retry().call("confirmation du retrait " + operationId,
                () -> ports.ledger().confirmWithdrawal(operationId, delivered));
        long undelivered = reservation.quantity() - delivered;
        if (confirmed.isEmpty() || !confirmed.get().isEffective()) {
            LOGGER.log(System.Logger.Level.ERROR,
                    ConsoleText.tr("Retrait {0} non confirme en base : {1} x {2} remis au joueur {3} sur {4}. Il reste RESERVED et sera solde au prochain demarrage avec 0 remis : verifier manuellement.",
                            operationId, delivered, item, playerId, reservation.quantity()));
            finish(TransferResult.moved(TransferStatus.CONFIRMATION_PENDING, TYPE, bank, item, delivered, undelivered,
                    reservation.valueDelta(), null));
            return;
        }
        OperationResult settled = confirmed.get();
        TransferResult result = TransferResult.moved(TransferStatus.SUCCESS, TYPE, bank, item, delivered, undelivered,
                reservation.valueDelta().add(settled.valueDelta()), settled.bankValue().orElse(null));
        onPlayer(() -> {
            if (delivered > 0 && rules.savePlayerData()) {
                ports.playerData().save(playerId);
            }
            finish(result);
        }, () -> finish(result));
    }

    @Override
    void failed(RuntimeException failure) {
        LOGGER.log(System.Logger.Level.ERROR, reservation == null ? ConsoleText.tr("Retrait {0} du joueur {1} interrompu : {2}",
                operationId, playerId, failure)
                : ConsoleText.tr("Retrait {0} du joueur {1} interrompu apres reservation (solde au prochain demarrage) : {2}",
                        operationId, playerId, failure),
                failure);
        finish(TransferResult.refused(TYPE, TransferStatus.INTERNAL_ERROR));
    }

    private static long stockOf(Optional<Bank> bank, ItemKey item) {
        return bank.map(found -> found.line(item).quantity()).orElse(0L);
    }
}
