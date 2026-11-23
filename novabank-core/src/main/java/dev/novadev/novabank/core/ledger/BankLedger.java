package dev.novadev.novabank.core.ledger;

import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.api.OperationId;
import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.api.operation.OperationResult;
import dev.novadev.novabank.api.operation.OperationStatus;
import dev.novadev.novabank.api.valuation.ValuationMode;
import dev.novadev.novabank.core.bank.Bank;
import dev.novadev.novabank.core.delivery.PendingDeliveryStore;
import dev.novadev.novabank.core.delivery.UndeliveredPolicy;
import dev.novadev.novabank.core.journal.OperationRecord;
import dev.novadev.novabank.core.repository.BankRepository;
import dev.novadev.novabank.core.repository.MutationDecision;
import dev.novadev.novabank.core.valuation.Movement;
import dev.novadev.novabank.core.valuation.PriceCatalog;
import dev.novadev.novabank.core.valuation.StockAccounting;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Service metier des banques : depot, retrait en deux temps et reset.
 *
 * <p>Chaque operation porte un {@link OperationId} fourni par l'appelant et peut etre
 * rejouee sans risque : un rejeu renvoie {@link OperationStatus#DUPLICATE_OPERATION}
 * avec les donnees de l'application d'origine, sans second effet. Les controles de
 * stock et de catalogue sont faits dans l'unite atomique du stockage, jamais avant.
 *
 * <p>Retrait : {@link #reserveWithdrawal} debite la banque, l'appelant remet les items,
 * puis {@link #confirmWithdrawal} (avec la quantite reellement remise) ou
 * {@link #cancelWithdrawal} solde le retrait.
 */
public final class BankLedger {

    private final BankRepository repository;
    private final StockAccounting accounting;
    private final Clock clock;
    private final OperationResults results;
    private final WithdrawalSettlement settlement;

    /**
     * @param repository stockage des banques et du journal
     * @param catalog    catalogue de prix courant
     * @param deliveries file des livraisons en attente
     * @param policy     devenir des unites non livrees
     * @param clock      horloge d'horodatage du journal
     */
    public BankLedger(BankRepository repository, PriceCatalog catalog, PendingDeliveryStore deliveries,
                      UndeliveredPolicy policy, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.accounting = new StockAccounting(Objects.requireNonNull(catalog, "catalog"));
        this.clock = Objects.requireNonNull(clock, "clock");
        this.results = new OperationResults(repository, accounting);
        this.settlement = new WithdrawalSettlement(repository, accounting,
                Objects.requireNonNull(deliveries, "deliveries"), Objects.requireNonNull(policy, "policy"), results);
    }

    /**
     * Ouvre une banque vide, de facon idempotente.
     *
     * @param bankId        identifiant
     * @param owner         proprietaire
     * @param valuationMode mode de valorisation
     * @return la banque creee, ou l'existante si l'identifiant etait deja pris
     */
    public Bank openBank(BankId bankId, EntityRef owner, ValuationMode valuationMode) {
        return repository.createBank(bankId, owner, valuationMode);
    }

    /**
     * Valeur courante d'une banque.
     *
     * @param bankId banque
     * @return sa valeur, ou vide si elle n'existe pas
     */
    public Optional<BigDecimal> valueOf(BankId bankId) {
        return repository.findBank(bankId).map(accounting::bankValue);
    }

    /**
     * Valeur d'un etat de banque deja lu, au catalogue courant (sans acces au stockage).
     *
     * @param bank banque
     * @return sa valeur
     */
    public BigDecimal valueOf(Bank bank) {
        return accounting.bankValue(Objects.requireNonNull(bank, "bank"));
    }

    /**
     * Valeur de chaque ligne d'un etat de banque deja lu, au catalogue courant.
     *
     * @param bank banque
     * @return la valeur par item
     */
    public Map<ItemKey, BigDecimal> lineValues(Bank bank) {
        return accounting.lineValues(Objects.requireNonNull(bank, "bank"));
    }

    /**
     * Depose des items.
     *
     * @param id       identifiant de l'operation
     * @param bankId   banque cible
     * @param item     item depose
     * @param quantity quantite, positive
     * @return le resultat ; refus possibles : INVALID_QUANTITY, NOT_ACCEPTED,
     *         QUANTITY_OVERFLOW, UNKNOWN_BANK
     */
    public OperationResult deposit(OperationId id, BankId bankId, ItemKey item, long quantity) {
        requireIds(id, bankId, item);
        if (quantity <= 0) {
            return OperationResult.rejected(id, OperationStatus.INVALID_QUANTITY);
        }
        return results.ofInitialStep(id, repository.apply(id, bankId, bank -> OverflowGuard.decide(() ->
                accounting.deposit(bank, item, quantity)
                        .map(movement -> MutationDecision.accept(movement.bank(), OperationRecord.deposit(
                                id, bankId, item, quantity, movement.countedQuantity(), movement.valueDelta(),
                                clock.instant())))
                        .orElseGet(() -> MutationDecision.reject(OperationStatus.NOT_ACCEPTED)))));
    }

    /**
     * Reserve un retrait : le stock et la valeur sont debites immediatement.
     *
     * @param id        identifiant de l'operation, reutilise pour la confirmation
     * @param bankId    banque source
     * @param item      item retire (meme hors catalogue)
     * @param quantity  quantite, positive
     * @param recipient joueur destinataire des items
     * @return le resultat ; refus possibles : INVALID_QUANTITY, INSUFFICIENT_STOCK, UNKNOWN_BANK
     */
    public OperationResult reserveWithdrawal(OperationId id, BankId bankId, ItemKey item, long quantity,
                                             UUID recipient) {
        requireIds(id, bankId, item);
        Objects.requireNonNull(recipient, "recipient");
        if (quantity <= 0) {
            return OperationResult.rejected(id, OperationStatus.INVALID_QUANTITY);
        }
        return results.ofInitialStep(id, repository.apply(id, bankId, bank -> {
            if (bank.line(item).quantity() < quantity) {
                return MutationDecision.reject(OperationStatus.INSUFFICIENT_STOCK);
            }
            Movement movement = accounting.withdraw(bank, item, quantity);
            return MutationDecision.accept(movement.bank(), OperationRecord.reservation(id, bankId, item, quantity,
                    movement.countedQuantity(), movement.valueDelta(), clock.instant(), recipient, bank.resetEpoch()));
        }));
    }

    /**
     * Confirme un retrait reserve. Les unites non livrees suivent la
     * {@link UndeliveredPolicy} configuree.
     *
     * @param id                retrait reserve
     * @param deliveredQuantity unites reellement remises, entre 0 et la quantite reservee
     * @return le resultat (quantite livree, valeur re-creditee) ; refus possibles :
     *         UNKNOWN_OPERATION, INVALID_QUANTITY, ALREADY_SETTLED, QUANTITY_OVERFLOW
     */
    public OperationResult confirmWithdrawal(OperationId id, long deliveredQuantity) {
        return settlement.confirm(Objects.requireNonNull(id, "id"), deliveredQuantity);
    }

    /**
     * Annule un retrait reserve : tout est rendu a la banque a l'identique.
     *
     * @param id retrait reserve
     * @return le resultat (quantite rendue, valeur re-creditee) ; refus possibles :
     *         UNKNOWN_OPERATION, ALREADY_SETTLED, QUANTITY_OVERFLOW
     */
    public OperationResult cancelWithdrawal(OperationId id) {
        return settlement.cancel(Objects.requireNonNull(id, "id"));
    }

    /**
     * Reinitialise la valeur d'une banque sans toucher a son stock.
     *
     * @param id     identifiant de l'operation
     * @param bankId banque
     * @return le resultat (variation = -valeur effacee) ; refus possibles : UNKNOWN_BANK
     */
    public OperationResult reset(OperationId id, BankId bankId) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(bankId, "bankId");
        return results.ofInitialStep(id, repository.apply(id, bankId, bank -> OverflowGuard.decide(() -> {
            Movement movement = accounting.reset(bank);
            return MutationDecision.accept(movement.bank(), OperationRecord.reset(id, bankId, bank.totalQuantity(),
                    movement.countedQuantity(), movement.valueDelta(), clock.instant()));
        })));
    }

    private static void requireIds(OperationId id, BankId bankId, ItemKey item) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(bankId, "bankId");
        Objects.requireNonNull(item, "item");
    }
}
