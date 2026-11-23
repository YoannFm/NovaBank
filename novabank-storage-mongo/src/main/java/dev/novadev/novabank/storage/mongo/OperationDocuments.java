package dev.novadev.novabank.storage.mongo;

import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.api.OperationId;
import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.api.operation.OperationType;
import dev.novadev.novabank.core.journal.OperationRecord;
import dev.novadev.novabank.core.journal.OperationState;
import dev.novadev.novabank.core.journal.WithdrawalProgress;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.bson.Document;

/**
 * Conversion d'une entree de journal en document de la collection {@code operations} et
 * inversement. L'horodatage est stocke en secondes + nanosecondes (precision complete de
 * {@link Instant}, ordre identique au stockage SQL).
 *
 * <p>Le champ technique {@value #BANK_VERSION} porte la version de la banque ecrite avec l'entree :
 * le remplacement d'une entree n'est jamais rejoue par-dessus une evolution plus recente (voir
 * {@link PendingOperations}).
 */
final class OperationDocuments {

    static final String ID = "_id";
    static final String BANK_ID = "bankId";
    static final String TYPE = "type";
    static final String STATE = "state";
    static final String AT_SECONDS = "atSec";
    static final String AT_NANOS = "atNano";
    static final String BANK_VERSION = "bankVersion";

    private static final String ITEM = "item";
    private static final String QUANTITY = "quantity";
    private static final String COUNTED = "counted";
    private static final String VALUE_DELTA = "valueDelta";
    private static final String RECIPIENT = "recipient";
    private static final String RESERVED_EPOCH = "reservedEpoch";
    private static final String DELIVERED = "delivered";
    private static final String RETURNED = "returned";
    private static final String RETURNED_VALUE = "returnedValue";

    private OperationDocuments() {
    }

    /**
     * @param record      entree de journal
     * @param bankVersion version de la banque ecrite avec cette entree
     * @return son document
     */
    static Document toDocument(OperationRecord record, long bankVersion) {
        Document document = new Document(ID, record.operationId().toString())
                .append(BANK_ID, record.bankId().toString())
                .append(TYPE, record.type().name());
        if (record.itemKey() != null) {
            document.append(ITEM, record.itemKey().toString());
        }
        document.append(QUANTITY, record.quantity()).append(COUNTED, record.countedQuantity())
                .append(VALUE_DELTA, BankDocuments.money(record.valueDelta()))
                .append(STATE, record.state().name())
                .append(AT_SECONDS, record.timestamp().getEpochSecond())
                .append(AT_NANOS, record.timestamp().getNano())
                .append(BANK_VERSION, bankVersion);
        WithdrawalProgress withdrawal = record.withdrawal();
        if (withdrawal != null) {
            document.append(RECIPIENT, withdrawal.recipient().toString())
                    .append(RESERVED_EPOCH, withdrawal.reservedEpoch())
                    .append(DELIVERED, withdrawal.deliveredQuantity())
                    .append(RETURNED, withdrawal.returnedQuantity())
                    .append(RETURNED_VALUE, BankDocuments.money(withdrawal.returnedValue()));
        }
        return document;
    }

    /**
     * @param document document de la collection {@code operations}
     * @return l'entree de journal
     */
    static OperationRecord fromDocument(Document document) {
        String item = document.getString(ITEM);
        WithdrawalProgress withdrawal = null;
        if (document.containsKey(RECIPIENT)) {
            withdrawal = new WithdrawalProgress(UUID.fromString(document.getString(RECIPIENT)),
                    document.getLong(RESERVED_EPOCH), document.getLong(DELIVERED), document.getLong(RETURNED),
                    new BigDecimal(document.getString(RETURNED_VALUE)));
        }
        return new OperationRecord(new OperationId(UUID.fromString(document.getString(ID))),
                OperationType.valueOf(document.getString(TYPE)),
                new BankId(UUID.fromString(document.getString(BANK_ID))),
                item == null ? null : ItemKey.parse(item),
                document.getLong(QUANTITY), document.getLong(COUNTED), new BigDecimal(document.getString(VALUE_DELTA)),
                OperationState.valueOf(document.getString(STATE)),
                Instant.ofEpochSecond(document.getLong(AT_SECONDS), document.getInteger(AT_NANOS)), withdrawal);
    }
}
