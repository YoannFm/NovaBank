package dev.novadev.novabank.bukkit.publicapi;

import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.api.BankInfo;
import dev.novadev.novabank.api.NovaBankApi;
import dev.novadev.novabank.api.OperationId;
import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.api.entity.OwnershipProvider;
import dev.novadev.novabank.api.operation.OperationInfo;
import dev.novadev.novabank.api.operation.OperationResult;
import dev.novadev.novabank.api.operation.OperationStatus;
import dev.novadev.novabank.api.ranking.LeaderboardLine;
import dev.novadev.novabank.api.valuation.ValuationMode;
import dev.novadev.novabank.core.application.TaskScheduler;
import dev.novadev.novabank.core.bank.Bank;
import dev.novadev.novabank.core.bank.StockLine;
import dev.novadev.novabank.core.ledger.BankLedger;
import dev.novadev.novabank.core.ownership.OwnershipRegistry;
import dev.novadev.novabank.core.ranking.LeaderboardService;
import dev.novadev.novabank.core.repository.BankRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

/**
 * {@link NovaBankApi} au-dessus du grand livre et du stockage. Tout acces au stockage passe par l'executeur
 * d'entrees/sorties du plugin ; un executeur sature ou arrete acheve le futur exceptionnellement.
 */
public final class NovaBankApiImpl implements NovaBankApi {

    /** Beneficiaire des retraits faits par l'API : aucun joueur (l'appelant remet lui-meme les items). */
    private static final UUID NO_RECIPIENT = new UUID(0L, 0L);

    private final BankLedger ledger;
    private final BankRepository banks;
    private final LeaderboardService leaderboards;
    private final TaskScheduler scheduler;
    private final Supplier<ValuationMode> valuationMode;
    private final OwnershipRegistry ownership;

    /**
     * @param ledger        grand livre (depots, retraits, valeurs)
     * @param banks         lecture des banques
     * @param leaderboards  classements en memoire
     * @param scheduler     planificateur (executeur asynchrone)
     * @param valuationMode mode de valeur des banques creees, lu a chaque usage
     * @param ownership     registre des fournisseurs d'appartenance
     */
    public NovaBankApiImpl(BankLedger ledger, BankRepository banks, LeaderboardService leaderboards,
                           TaskScheduler scheduler, Supplier<ValuationMode> valuationMode,
                           OwnershipRegistry ownership) {
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.banks = Objects.requireNonNull(banks, "banks");
        this.leaderboards = Objects.requireNonNull(leaderboards, "leaderboards");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.valuationMode = Objects.requireNonNull(valuationMode, "valuationMode");
        this.ownership = Objects.requireNonNull(ownership, "ownership");
    }

    @Override
    public BankId bankId(EntityRef owner) {
        return BankId.forOwner(Objects.requireNonNull(owner, "owner"));
    }

    @Override
    public CompletableFuture<Optional<BankInfo>> bank(BankId id) {
        Objects.requireNonNull(id, "id");
        return async(() -> banks.findBank(id).map(this::info));
    }

    @Override
    public CompletableFuture<OperationResult> deposit(OperationId operationId, EntityRef owner, ItemKey item,
                                                      long quantity) {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(item, "item");
        BankId id = bankId(owner);
        return async(() -> {
            ledger.openBank(id, owner, valuationMode.get());
            return ledger.deposit(operationId, id, item, quantity);
        });
    }

    @Override
    public CompletableFuture<OperationResult> withdraw(OperationId operationId, EntityRef owner, ItemKey item,
                                                       long quantity) {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(item, "item");
        BankId id = bankId(owner);
        return async(() -> {
            OperationResult reserved = ledger.reserveWithdrawal(operationId, id, item, quantity, NO_RECIPIENT);
            // Rejeu : le resultat d'origine est rendu tel quel, sans second solde.
            if (reserved.status() != OperationStatus.SUCCESS) {
                return reserved;
            }
            return ledger.confirmWithdrawal(operationId, quantity);
        });
    }

    @Override
    public CompletableFuture<BankInfo> createBank(EntityRef owner) {
        BankId id = bankId(owner);
        return async(() -> info(ledger.openBank(id, owner, valuationMode.get())));
    }

    @Override
    public CompletableFuture<OperationResult> resetValue(OperationId operationId, EntityRef owner) {
        Objects.requireNonNull(operationId, "operationId");
        BankId id = bankId(owner);
        return async(() -> ledger.reset(operationId, id));
    }

    @Override
    public CompletableFuture<Optional<OperationInfo>> operation(OperationId operationId) {
        Objects.requireNonNull(operationId, "operationId");
        return async(() -> banks.findOperation(operationId).map(record -> new OperationInfo(record.operationId(),
                record.type(), record.bankId(), record.itemKey(), record.quantity(), record.valueDelta(),
                record.state().name(), record.timestamp())));
    }

    @Override
    public java.util.SortedSet<String> entityTypes() {
        return ownership.types();
    }

    @Override
    public boolean registerOwnershipProvider(OwnershipProvider provider) {
        try {
            ownership.register(Objects.requireNonNull(provider, "provider"));
            return true;
        } catch (IllegalStateException alreadyHandled) {
            return false;
        }
    }

    @Override
    public void unregisterOwnershipProvider(String entityType) {
        ownership.unregister(Objects.requireNonNull(entityType, "entityType"));
    }

    @Override
    public List<LeaderboardLine> top(String type, int limit) {
        return leaderboards.top(Objects.requireNonNull(type, "type"), limit).stream()
                .map(entry -> new LeaderboardLine(entry.rank(), entry.bankId(), entry.owner(), entry.value())).toList();
    }

    private BankInfo info(Bank bank) {
        Map<ItemKey, Long> stock = new HashMap<>();
        for (Map.Entry<ItemKey, StockLine> line : bank.lines().entrySet()) {
            if (line.getValue().quantity() > 0) {
                stock.put(line.getKey(), line.getValue().quantity());
            }
        }
        return new BankInfo(bank.id(), bank.owner(), bank.valuationMode(), ledger.valueOf(bank), stock, bank.version());
    }

    private <T> CompletableFuture<T> async(Supplier<T> work) {
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            scheduler.runAsync(() -> {
                try {
                    future.complete(work.get());
                } catch (RuntimeException failure) {
                    future.completeExceptionally(failure);
                }
            });
        } catch (RejectedExecutionException saturated) {
            future.completeExceptionally(saturated);
        }
        return future;
    }
}
