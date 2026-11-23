package dev.novadev.novabank.core.ledger;

import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.api.OperationId;
import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.api.operation.OperationResult;
import dev.novadev.novabank.api.valuation.ValuationMode;
import dev.novadev.novabank.core.bank.Bank;
import dev.novadev.novabank.core.bank.StockLine;
import dev.novadev.novabank.core.delivery.UndeliveredPolicy;
import dev.novadev.novabank.core.journal.OperationRecord;
import dev.novadev.novabank.core.testing.InMemoryBankRepository;
import dev.novadev.novabank.core.testing.InMemoryPendingDeliveryStore;
import dev.novadev.novabank.core.testing.MutablePriceCatalog;
import dev.novadev.novabank.core.testing.TestBankIds;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Montage d'un {@link BankLedger} sur les doublures en memoire, avec une banque ouverte.
 */
final class LedgerFixture {

    static final ItemKey STONE = ItemKey.minecraft("stone");
    static final ItemKey DIAMOND = ItemKey.minecraft("diamond_block");
    static final ItemKey RUBY = ItemKey.parse("itemsadder:pack/ruby_block");
    static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-00000000cafe");
    static final Instant NOW = Instant.parse("2026-10-22T12:00:00Z");

    final InMemoryBankRepository repository = new InMemoryBankRepository();
    final MutablePriceCatalog catalog = new MutablePriceCatalog()
            .price(STONE, "1")
            .price(DIAMOND, "2.5")
            .price(RUBY, "10");
    final InMemoryPendingDeliveryStore deliveries = new InMemoryPendingDeliveryStore();
    final BankLedger ledger;
    final BankId bankId = TestBankIds.random();

    private LedgerFixture(ValuationMode mode, UndeliveredPolicy policy) {
        ledger = new BankLedger(repository, catalog, deliveries, policy, Clock.fixed(NOW, ZoneOffset.UTC));
        ledger.openBank(bankId, EntityRef.player(PLAYER), mode);
    }

    static LedgerFixture of(ValuationMode mode) {
        return new LedgerFixture(mode, UndeliveredPolicy.RETURN_TO_BANK);
    }

    static LedgerFixture of(ValuationMode mode, UndeliveredPolicy policy) {
        return new LedgerFixture(mode, policy);
    }

    OperationResult deposit(ItemKey item, long quantity) {
        return ledger.deposit(OperationId.random(), bankId, item, quantity);
    }

    OperationResult reserve(OperationId id, ItemKey item, long quantity) {
        return ledger.reserveWithdrawal(id, bankId, item, quantity, PLAYER);
    }

    /** Retrait complet (reservation puis confirmation totale). */
    OperationResult withdraw(ItemKey item, long quantity) {
        OperationId id = OperationId.random();
        OperationResult reserved = reserve(id, item, quantity);
        if (reserved.isSuccess()) {
            ledger.confirmWithdrawal(id, quantity);
        }
        return reserved;
    }

    OperationResult reset() {
        return ledger.reset(OperationId.random(), bankId);
    }

    BigDecimal value() {
        return ledger.valueOf(bankId).orElseThrow();
    }

    Bank bank() {
        return repository.findBank(bankId).orElseThrow();
    }

    StockLine line(ItemKey item) {
        return bank().line(item);
    }

    OperationRecord record(OperationId id) {
        return repository.findOperation(id).orElseThrow();
    }
}
