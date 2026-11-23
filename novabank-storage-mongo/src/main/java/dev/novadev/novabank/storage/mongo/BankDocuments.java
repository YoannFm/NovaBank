package dev.novadev.novabank.storage.mongo;

import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.api.valuation.ValuationMode;
import dev.novadev.novabank.core.bank.Bank;
import dev.novadev.novabank.core.bank.StockLine;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bson.Document;

/**
 * Conversion d'une banque en document de la collection {@code banks} et inversement.
 *
 * <pre>
 * { _id: "uuid", ownerType, ownerId, mode, version: long, resetEpoch: long,
 *   lines: [ { item: "minecraft:stone", quantity: long, baseline: long, value: "12.3400" } ],
 *   updatedAt: date, pendingOp: { ... } (voir PendingOperations, absent en temps normal) }
 * </pre>
 *
 * <p>Les montants sont stockes en texte decimal canonique ({@link BigDecimal#toPlainString()},
 * echelle 4) : relecture exacte, sans limite de precision.
 */
final class BankDocuments {

    static final String ID = "_id";
    static final String VERSION = "version";
    static final String RESET_EPOCH = "resetEpoch";
    static final String LINES = "lines";
    static final String PENDING = "pendingOp";
    static final String UPDATED_AT = "updatedAt";

    private static final String OWNER_TYPE = "ownerType";
    private static final String OWNER_ID = "ownerId";
    private static final String MODE = "mode";
    private static final String ITEM = "item";
    private static final String QUANTITY = "quantity";
    private static final String BASELINE = "baseline";
    private static final String VALUE = "value";

    private final Clock clock;

    BankDocuments(Clock clock) {
        this.clock = clock;
    }

    /**
     * @param bank banque
     * @return le document complet d'une banque neuve (sans operation en attente)
     */
    Document toDocument(Bank bank) {
        Document document = new Document(ID, bank.id().toString())
                .append(OWNER_TYPE, bank.owner().type())
                .append(OWNER_ID, bank.owner().id())
                .append(MODE, bank.valuationMode().name());
        return document.append(VERSION, bank.version()).append(RESET_EPOCH, bank.resetEpoch())
                .append(LINES, lines(bank)).append(UPDATED_AT, new Date(clock.millis()));
    }

    /**
     * @param bank etat enregistre
     * @return les champs modifiables d'une banque, pour un {@code $set}
     */
    Document mutableFields(Bank bank) {
        return new Document(VERSION, bank.version()).append(RESET_EPOCH, bank.resetEpoch())
                .append(LINES, lines(bank)).append(UPDATED_AT, new Date(clock.millis()));
    }

    /**
     * @param bank banque
     * @return ses lignes de stock, triees par item
     */
    List<Document> lines(Bank bank) {
        List<Map.Entry<ItemKey, StockLine>> entries = new ArrayList<>(bank.lines().entrySet());
        entries.sort(Comparator.comparing(entry -> entry.getKey().toString()));
        List<Document> lines = new ArrayList<>(entries.size());
        for (Map.Entry<ItemKey, StockLine> entry : entries) {
            StockLine line = entry.getValue();
            lines.add(new Document(ITEM, entry.getKey().toString()).append(QUANTITY, line.quantity())
                    .append(BASELINE, line.baselineQuantity()).append(VALUE, money(line.accumulatedValue())));
        }
        return lines;
    }

    /**
     * @param document document de la collection {@code banks}
     * @return la banque (l'operation en attente eventuelle est ignoree)
     */
    Bank fromDocument(Document document) {
        Map<ItemKey, StockLine> lines = new HashMap<>();
        for (Document line : document.getList(LINES, Document.class, List.of())) {
            lines.put(ItemKey.parse(line.getString(ITEM)), new StockLine(line.getLong(QUANTITY), line.getLong(BASELINE),
                    new BigDecimal(line.getString(VALUE))));
        }
        return new Bank(new BankId(UUID.fromString(document.getString(ID))),
                new EntityRef(document.getString(OWNER_TYPE), document.getString(OWNER_ID)),
                ValuationMode.valueOf(document.getString(MODE)), document.getLong(VERSION),
                document.getLong(RESET_EPOCH), lines);
    }

    /**
     * @param amount montant a l'echelle monetaire
     * @return son texte canonique
     */
    static String money(BigDecimal amount) {
        return amount.toPlainString();
    }
}
