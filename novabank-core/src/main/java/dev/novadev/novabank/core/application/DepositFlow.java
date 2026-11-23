package dev.novadev.novabank.core.application;

import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.api.OperationId;
import dev.novadev.novabank.api.entity.EntityMembership;
import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.api.operation.OperationResult;
import dev.novadev.novabank.api.operation.OperationType;
import dev.novadev.novabank.core.access.Permissions;
import dev.novadev.novabank.core.delivery.PendingDelivery;
import dev.novadev.novabank.core.journal.OperationRecord;
import dev.novadev.novabank.core.text.ConsoleText;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Depot : une operation par item, toutes dans la banque active du joueur.
 *
 * <ol>
 *   <li>thread joueur : permission, appartenances ;</li>
 *   <li>IO : banque active, droit du role ;</li>
 *   <li>thread joueur : controle des quantites, retrait de k unites identifiees de l'inventaire ;</li>
 *   <li>IO : {@code deposit(opId)} avec nouvelles tentatives (idempotent) ;</li>
 *   <li>thread joueur : items des depots refuses rendus (le surplus et les joueurs partis
 *       passent en livraison differee, jamais perdus), puis sauvegarde des donnees du
 *       joueur si au moins un depot est valide.</li>
 * </ol>
 *
 * <p>Issue inconnue apres toutes les tentatives : le journal est relu ; s'il est lui aussi
 * illisible, les items ne sont PAS rendus (une double remise serait une duplication) et
 * l'operation est journalisee en SEVERE pour une verification manuelle.
 */
final class DepositFlow extends Flow<List<TransferResult>> {

    private static final OperationType TYPE = OperationType.DEPOSIT;

    private final ItemChoice choice;
    private final Amount amount;
    private final TransferRules rules;
    private final List<DepositLine> taken = new ArrayList<>();
    private final List<TransferResult> refusals = new ArrayList<>();
    private EntityRef bank;

    DepositFlow(OperationPorts ports, UUID playerId, ItemChoice choice, Amount amount, TransferRules rules,
                Consumer<List<TransferResult>> done, Runnable release) {
        super(ports, playerId, done, release);
        this.choice = choice;
        this.amount = amount;
        this.rules = rules;
    }

    void start() {
        onPlayer(this::checkAccess, this::offlineBeforeTaking);
    }

    private void checkAccess() {
        if (!ports.permissions().has(playerId, Permissions.DEPOSIT)) {
            finish(List.of(TransferResult.refused(TYPE, TransferStatus.NO_PERMISSION)));
            return;
        }
        List<EntityMembership> memberships = ports.ownership().memberships(playerId, rules.enabledTypes());
        Map<EntityRef, Map<OperationType, Boolean>> hostRights = ports.ownership().rights(playerId, memberships);
        onIo(() -> chooseBank(memberships, hostRights));
    }

    private void chooseBank(List<EntityMembership> memberships, Map<EntityRef, Map<OperationType, Boolean>> hostRights) {
        Optional<EntityMembership> active = BankResolution.active(ports, playerId, memberships, rules);
        if (active.isEmpty()) {
            finish(List.of(TransferResult.refused(TYPE, TransferStatus.NO_BANK)));
            return;
        }
        bank = active.get().entity();
        if (!BankResolution.allowed(rules, hostRights, active.get(), TYPE)) {
            finish(List.of(TransferResult.refused(TYPE, TransferStatus.ROLE_NOT_ALLOWED, bank, null)));
            return;
        }
        onPlayer(this::takeItems, this::offlineBeforeTaking);
    }

    private void offlineBeforeTaking() {
        finish(List.of(TransferResult.refused(TYPE, interruptedStatus())));
    }

    private void takeItems() {
        List<ItemKey> items = itemsToDeposit();
        if (items.isEmpty()) {
            TransferStatus status = choice.kind() == ItemChoice.Kind.HELD
                    ? TransferStatus.NOTHING_IN_HAND : TransferStatus.NOTHING_TO_DEPOSIT;
            finish(List.of(TransferResult.refused(TYPE, status, bank, null)));
            return;
        }
        for (ItemKey item : items) {
            take(item).ifPresent(status -> refusals.add(TransferResult.refused(TYPE, status, bank, item)));
        }
        if (taken.isEmpty()) {
            finish(List.copyOf(refusals));
        } else {
            onIo(this::record);
        }
    }

    private List<ItemKey> itemsToDeposit() {
        return switch (choice.kind()) {
            case ITEM -> List.of(choice.item().orElseThrow());
            case HELD -> ports.inventory().heldItem(playerId).map(List::of).orElse(List.of());
            case ALL_ACCEPTED -> rules.acceptedItems().stream()
                    .filter(item -> ports.inventory().count(playerId, item) > 0)
                    .toList();
        };
    }

    private Optional<TransferStatus> take(ItemKey item) {
        if (!rules.accepts(item)) {
            return Optional.of(TransferStatus.NOT_ACCEPTED);
        }
        if (!amount.all() && amount.value() > rules.maxQuantityPerOperation()) {
            return Optional.of(TransferStatus.QUANTITY_LIMIT);
        }
        long available = ports.inventory().count(playerId, item);
        long wanted = amount.resolve(available, rules.maxQuantityPerOperation());
        if (wanted <= 0 || wanted > available) {
            return Optional.of(TransferStatus.NOT_ENOUGH_ITEMS);
        }
        long removed = ports.inventory().remove(playerId, item, wanted);
        if (removed <= 0) {
            return Optional.of(TransferStatus.NOT_ENOUGH_ITEMS);
        }
        taken.add(new DepositLine(OperationId.random(), item, removed));
        return Optional.empty();
    }

    private void record() {
        BankId bankId = BankId.forOwner(bank);
        ports.retry().call("ouverture de la banque " + bank,
                () -> ports.ledger().openBank(bankId, bank, rules.valuationMode()));
        for (DepositLine line : taken) {
            record(bankId, line);
        }
        onPlayer(this::settleOnPlayer, this::settleOffline);
    }

    private void record(BankId bankId, DepositLine line) {
        ports.actors().remember(line.id(), playerId);
        Optional<OperationResult> result = ports.retry().call("depot " + line.id(),
                () -> ports.ledger().deposit(line.id(), bankId, line.key(), line.quantity()));
        if (result.isPresent()) {
            line.settled(result.get());
            return;
        }
        Optional<Optional<OperationRecord>> journal = ports.retry().call("verification du depot " + line.id(),
                () -> ports.banks().findOperation(line.id()));
        if (journal.isEmpty()) {
            line.unknown();
            LOGGER.log(System.Logger.Level.ERROR,
                    ConsoleText.tr("Depot {0} d'issue inconnue (base indisponible) : {1} x {2} du joueur {3} vers {4}. Items NON rendus pour eviter une duplication : verifier le journal et rendre a la main si l'operation est absente.",
                            line.id(), line.quantity(), line.key(), playerId, bank));
        } else if (journal.get().isPresent()) {
            line.recorded(journal.get().get().valueDelta());
        } else {
            line.toRefund();
        }
    }

    private void settleOnPlayer() {
        List<PendingDelivery> overflow = new ArrayList<>();
        for (DepositLine line : taken) {
            if (line.mustBeReturned()) {
                long leftover = ports.inventory().give(playerId, line.key(), line.quantity());
                line.returned(leftover);
                if (leftover > 0) {
                    overflow.add(new PendingDelivery(line.id(), playerId, line.key(), leftover));
                }
            }
        }
        if (rules.savePlayerData() && taken.stream().anyMatch(DepositLine::isRecorded)) {
            ports.playerData().save(playerId);
        }
        storeThenFinish(overflow);
    }

    private void settleOffline() {
        List<PendingDelivery> overflow = new ArrayList<>();
        for (DepositLine line : taken) {
            if (line.mustBeReturned()) {
                line.returned(line.quantity());
                overflow.add(new PendingDelivery(line.id(), playerId, line.key(), line.quantity()));
            }
        }
        storeThenFinish(overflow);
    }

    private void storeThenFinish(List<PendingDelivery> overflow) {
        if (overflow.isEmpty()) {
            finish(results());
            return;
        }
        onIo(() -> {
            for (PendingDelivery delivery : overflow) {
                if (ports.retry().call("livraison differee " + delivery.operationId(),
                        () -> {
                            ports.deliveries().add(delivery);
                            return delivery;
                        }).isEmpty()) {
                    LOGGER.log(System.Logger.Level.ERROR,
                            ConsoleText.tr("Items a rendre manuellement au joueur {0} : {1} x {2} (depot {3} refuse, livraison differee impossible a enregistrer)",
                                    playerId, delivery.quantity(), delivery.itemKey(), delivery.operationId()));
                }
            }
            finish(results());
        });
    }

    private List<TransferResult> results() {
        List<TransferResult> results = new ArrayList<>(refusals);
        for (DepositLine line : taken) {
            results.add(line.toResult(bank));
        }
        return List.copyOf(results);
    }

    @Override
    void failed(RuntimeException failure) {
        LOGGER.log(System.Logger.Level.ERROR, ConsoleText.tr("Depot du joueur {0} interrompu : {1}",
                playerId, failure), failure);
        boolean toRefund = taken.stream().anyMatch(DepositLine::isPending);
        if (toRefund) {
            onPlayer(this::settleOnPlayer, this::settleOffline);
        } else {
            finish(List.of(TransferResult.refused(TYPE, TransferStatus.INTERNAL_ERROR)));
        }
    }
}
