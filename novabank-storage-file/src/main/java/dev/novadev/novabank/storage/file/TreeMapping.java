package dev.novadev.novabank.storage.file;

import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.api.OperationId;
import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.api.operation.OperationType;
import dev.novadev.novabank.api.valuation.ValuationMode;
import dev.novadev.novabank.core.bank.Bank;
import dev.novadev.novabank.core.bank.StockLine;
import dev.novadev.novabank.core.delivery.PendingDelivery;
import dev.novadev.novabank.core.journal.OperationRecord;
import dev.novadev.novabank.core.journal.OperationState;
import dev.novadev.novabank.core.journal.WithdrawalProgress;
import dev.novadev.novabank.core.storage.ActiveBankChoice;
import dev.novadev.novabank.core.storage.StoredDelivery;
import dev.novadev.novabank.core.text.ConsoleText;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Conversion d'un {@link Commit} en arbre de textes et inversement : la meme representation sert
 * aux lignes du journal et a l'instantane (YAML ou JSON). Nombres et montants en texte exact
 * (montants a 4 decimales, {@code toPlainString}), dates ISO-8601 a la nanoseconde.
 *
 * <pre>
 * banks:        [{id, owner-type, owner-id, mode, version, reset-epoch,
 *                 lines: [{item, quantity, baseline, value}]}]
 * operations:   [{id, type, bank, item?, quantity, counted, value-delta, state, at,
 *                 withdrawal?: {recipient, reserved-epoch, delivered, returned, returned-value}}]
 * deliveries:   [{operation, player, item, quantity, delivered, created-at}]
 * active-banks: [{player, entity-type, entity-id}]
 * </pre>
 *
 * Une liste vide est omise.
 */
final class TreeMapping {

    static final String BANKS = "banks";
    static final String OPERATIONS = "operations";
    static final String DELIVERIES = "deliveries";
    static final String ACTIVE_BANKS = "active-banks";

    private TreeMapping() {
    }

    /**
     * @param commit elements
     * @param tree   table qui recoit les listes (non vides)
     */
    static void write(Commit commit, Map<String, Object> tree) {
        putList(tree, BANKS, commit.banks(), TreeMapping::bank);
        putList(tree, OPERATIONS, commit.operations(), TreeMapping::operation);
        putList(tree, DELIVERIES, commit.deliveries(), TreeMapping::delivery);
        putList(tree, ACTIVE_BANKS, commit.activeBanks(), TreeMapping::activeBank);
    }

    /**
     * @param tree table lue
     * @return les elements
     * @throws CorruptDataException si un champ est absent ou invalide
     */
    static Commit read(TreeReader tree) {
        return new Commit(readList(tree, BANKS, TreeMapping::bank), readList(tree, OPERATIONS, TreeMapping::operation),
                readList(tree, DELIVERIES, TreeMapping::delivery), readList(tree, ACTIVE_BANKS, TreeMapping::activeBank));
    }

    private static <T> void putList(Map<String, Object> tree, String key, List<T> elements,
                                    Function<T, Map<String, Object>> mapper) {
        if (!elements.isEmpty()) {
            List<Object> list = new ArrayList<>(elements.size());
            elements.forEach(element -> list.add(mapper.apply(element)));
            tree.put(key, list);
        }
    }

    private static <T> List<T> readList(TreeReader tree, String key, Function<TreeReader, T> reader) {
        List<T> elements = new ArrayList<>();
        for (TreeReader child : tree.children(key)) {
            try {
                elements.add(reader.apply(child));
            } catch (IllegalArgumentException invalid) {
                throw new CorruptDataException(ConsoleText.tr("{0} : {1}",
                        child.path(), invalid.getMessage()), invalid);
            }
        }
        return elements;
    }

    private static Map<String, Object> bank(Bank bank) {
        Map<String, Object> tree = new LinkedHashMap<>();
        tree.put("id", bank.id().toString());
        tree.put("owner-type", bank.owner().type());
        tree.put("owner-id", bank.owner().id());
        tree.put("mode", bank.valuationMode().name());
        tree.put("version", Long.toString(bank.version()));
        tree.put("reset-epoch", Long.toString(bank.resetEpoch()));
        List<Map.Entry<ItemKey, StockLine>> entries = new ArrayList<>(bank.lines().entrySet());
        entries.sort(Comparator.comparing(entry -> entry.getKey().toString()));
        List<Object> lines = new ArrayList<>(entries.size());
        for (Map.Entry<ItemKey, StockLine> entry : entries) {
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("item", entry.getKey().toString());
            line.put("quantity", Long.toString(entry.getValue().quantity()));
            line.put("baseline", Long.toString(entry.getValue().baselineQuantity()));
            line.put("value", money(entry.getValue().accumulatedValue()));
            lines.add(line);
        }
        tree.put("lines", lines);
        return tree;
    }

    private static Bank bank(TreeReader tree) {
        Map<ItemKey, StockLine> lines = new HashMap<>();
        for (TreeReader line : tree.children("lines")) {
            ItemKey item = ItemKey.parse(line.text("item"));
            if (lines.put(item, new StockLine(line.longValue("quantity"), line.longValue("baseline"),
                    new BigDecimal(line.text("value")))) != null) {
                throw new CorruptDataException(ConsoleText.tr("{0} : item en double {1}", line.path(), item));
            }
        }
        return new Bank(new BankId(UUID.fromString(tree.text("id"))),
                new EntityRef(tree.text("owner-type"), tree.text("owner-id")),
                ValuationMode.valueOf(tree.text("mode")), tree.longValue("version"), tree.longValue("reset-epoch"),
                lines);
    }

    private static Map<String, Object> operation(OperationRecord record) {
        Map<String, Object> tree = new LinkedHashMap<>();
        tree.put("id", record.operationId().toString());
        tree.put("type", record.type().name());
        tree.put("bank", record.bankId().toString());
        if (record.itemKey() != null) {
            tree.put("item", record.itemKey().toString());
        }
        tree.put("quantity", Long.toString(record.quantity()));
        tree.put("counted", Long.toString(record.countedQuantity()));
        tree.put("value-delta", money(record.valueDelta()));
        tree.put("state", record.state().name());
        tree.put("at", record.timestamp().toString());
        WithdrawalProgress withdrawal = record.withdrawal();
        if (withdrawal != null) {
            Map<String, Object> progress = new LinkedHashMap<>();
            progress.put("recipient", withdrawal.recipient().toString());
            progress.put("reserved-epoch", Long.toString(withdrawal.reservedEpoch()));
            progress.put("delivered", Long.toString(withdrawal.deliveredQuantity()));
            progress.put("returned", Long.toString(withdrawal.returnedQuantity()));
            progress.put("returned-value", money(withdrawal.returnedValue()));
            tree.put("withdrawal", progress);
        }
        return tree;
    }

    private static OperationRecord operation(TreeReader tree) {
        WithdrawalProgress withdrawal = null;
        if (tree.has("withdrawal")) {
            TreeReader progress = tree.child("withdrawal");
            withdrawal = new WithdrawalProgress(UUID.fromString(progress.text("recipient")),
                    progress.longValue("reserved-epoch"), progress.longValue("delivered"),
                    progress.longValue("returned"), new BigDecimal(progress.text("returned-value")));
        }
        String item = tree.optionalText("item");
        return new OperationRecord(new OperationId(UUID.fromString(tree.text("id"))),
                OperationType.valueOf(tree.text("type")), new BankId(UUID.fromString(tree.text("bank"))),
                item == null ? null : ItemKey.parse(item), tree.longValue("quantity"), tree.longValue("counted"),
                new BigDecimal(tree.text("value-delta")), OperationState.valueOf(tree.text("state")),
                tree.instant("at"), withdrawal);
    }

    private static Map<String, Object> delivery(StoredDelivery stored) {
        Map<String, Object> tree = new LinkedHashMap<>();
        tree.put("operation", stored.delivery().operationId().toString());
        tree.put("player", stored.delivery().playerId().toString());
        tree.put("item", stored.delivery().itemKey().toString());
        tree.put("quantity", Long.toString(stored.delivery().quantity()));
        tree.put("delivered", Long.toString(stored.deliveredQuantity()));
        tree.put("created-at", stored.createdAt().toString());
        return tree;
    }

    private static StoredDelivery delivery(TreeReader tree) {
        return new StoredDelivery(new PendingDelivery(new OperationId(UUID.fromString(tree.text("operation"))),
                UUID.fromString(tree.text("player")), ItemKey.parse(tree.text("item")), tree.longValue("quantity")),
                tree.longValue("delivered"), tree.instant("created-at"));
    }

    private static Map<String, Object> activeBank(ActiveBankChoice choice) {
        Map<String, Object> tree = new LinkedHashMap<>();
        tree.put("player", choice.playerId().toString());
        tree.put("entity-type", choice.entity().type());
        tree.put("entity-id", choice.entity().id());
        return tree;
    }

    private static ActiveBankChoice activeBank(TreeReader tree) {
        return new ActiveBankChoice(UUID.fromString(tree.text("player")),
                new EntityRef(tree.text("entity-type"), tree.text("entity-id")));
    }

    private static String money(BigDecimal amount) {
        return amount.toPlainString();
    }
}
