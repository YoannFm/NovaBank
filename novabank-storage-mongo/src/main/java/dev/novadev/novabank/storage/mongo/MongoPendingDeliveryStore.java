package dev.novadev.novabank.storage.mongo;

import com.mongodb.MongoException;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import dev.novadev.novabank.api.OperationId;
import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.core.delivery.PendingDelivery;
import dev.novadev.novabank.core.delivery.PendingDeliveryStore;
import dev.novadev.novabank.core.storage.StoredDelivery;
import dev.novadev.novabank.core.text.ConsoleText;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bson.Document;
import org.bson.conversions.Bson;

/**
 * {@link PendingDeliveryStore} MongoDB, idempotent par operation d'origine ({@code _id}).
 *
 * <pre>{ _id: "operation", playerId, item, quantity: long, remaining: long, createdAt: long (ms) }</pre>
 *
 * <p>Une remise decremente {@code remaining} par une mise a jour conditionnelle atomique (jamais
 * au-dela du du) ; le document d'une livraison entierement remise est conserve pour que l'ajout
 * reste idempotent.
 */
final class MongoPendingDeliveryStore implements PendingDeliveryStore {

    static final String PLAYER = "playerId";
    static final String REMAINING = "remaining";
    static final String CREATED_AT = "createdAt";

    private static final String ID = "_id";
    private static final String ITEM = "item";
    private static final String QUANTITY = "quantity";

    private final MongoCollections collections;
    private final Clock clock;
    private final MongoFailures failures;

    MongoPendingDeliveryStore(MongoCollections collections, Clock clock, MongoFailures failures) {
        this.collections = collections;
        this.clock = clock;
        this.failures = failures;
    }

    @Override
    public void add(PendingDelivery delivery) {
        failures.call("ajout de la livraison " + delivery.operationId(), () -> {
            try {
                collections.deliveries().insertOne(toDocument(new StoredDelivery(delivery, 0L, clock.instant())));
            } catch (MongoException failure) {
                if (!MongoFailures.isDuplicateKey(failure)) {
                    throw failure;
                }
            }
            return null;
        });
    }

    @Override
    public Optional<PendingDelivery> find(OperationId operationId) {
        return failures.call("lecture de la livraison " + operationId, () -> Optional.ofNullable(collections.deliveries()
                .find(Filters.and(Filters.eq(ID, operationId.toString()), Filters.gt(REMAINING, 0L))).first())
                .map(MongoPendingDeliveryStore::fromDocument));
    }

    @Override
    public List<PendingDelivery> pendingFor(UUID playerId) {
        return failures.call("livraisons du joueur " + playerId, () -> {
            Bson filter = Filters.and(Filters.eq(PLAYER, playerId.toString()), Filters.gt(REMAINING, 0L));
            List<PendingDelivery> deliveries = new ArrayList<>();
            for (Document document : collections.deliveries().find(filter).sort(Sorts.ascending(CREATED_AT, ID))) {
                deliveries.add(fromDocument(document));
            }
            return deliveries;
        });
    }

    @Override
    public boolean markDelivered(OperationId operationId, long quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException(ConsoleText.tr("Quantite remise non positive : {0}", quantity));
        }
        return failures.call("remise de la livraison " + operationId, () -> collections.deliveries().updateOne(
                Filters.and(Filters.eq(ID, operationId.toString()), Filters.gte(REMAINING, quantity)),
                Updates.inc(REMAINING, -quantity)).getModifiedCount() == 1);
    }

    /**
     * @param stored livraison, quantite remise et date d'enregistrement
     * @return son document
     */
    static Document toDocument(StoredDelivery stored) {
        PendingDelivery delivery = stored.delivery();
        return new Document(ID, delivery.operationId().toString())
                .append(PLAYER, delivery.playerId().toString()).append(ITEM, delivery.itemKey().toString())
                .append(QUANTITY, delivery.quantity()).append(REMAINING, stored.remainingQuantity())
                .append(CREATED_AT, stored.createdAt().toEpochMilli());
    }

    /**
     * @param document document d'une livraison
     * @return son etat complet (quantite d'origine, quantite remise)
     */
    static StoredDelivery storedFromDocument(Document document) {
        long quantity = document.getLong(QUANTITY);
        return new StoredDelivery(new PendingDelivery(new OperationId(UUID.fromString(document.getString(ID))),
                UUID.fromString(document.getString(PLAYER)), ItemKey.parse(document.getString(ITEM)), quantity),
                quantity - document.getLong(REMAINING), Instant.ofEpochMilli(document.getLong(CREATED_AT)));
    }

    private static PendingDelivery fromDocument(Document document) {
        return new PendingDelivery(new OperationId(UUID.fromString(document.getString(ID))),
                UUID.fromString(document.getString(PLAYER)), ItemKey.parse(document.getString(ITEM)),
                document.getLong(REMAINING));
    }
}
