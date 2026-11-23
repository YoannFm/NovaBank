package dev.novadev.novabank.storage.mongo;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexModel;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import dev.novadev.novabank.api.operation.OperationType;
import dev.novadev.novabank.core.journal.OperationState;
import dev.novadev.novabank.core.repository.StorageException;
import dev.novadev.novabank.core.text.ConsoleText;
import java.time.Clock;
import java.util.Date;
import java.util.List;
import org.bson.Document;

/**
 * Schema MongoDB : document {@code { _id: "schema", version }} et index, crees au demarrage de
 * facon idempotente (plusieurs serveurs peuvent demarrer ensemble). Un schema plus recent que
 * celui du module est refuse.
 *
 * <p>Version 1 : collections {@code banks}, {@code operations}, {@code pending_deliveries},
 * {@code active_banks} ; index du journal par banque (historique), des retraits reserves
 * (partiel), des operations en attente (partiel) et des livraisons par joueur.
 */
final class MongoSchema {

    /** Version de schema geree par ce module. */
    static final int VERSION = 1;

    private static final String SCHEMA_ID = "schema";
    private static final String VERSION_FIELD = "version";

    private final MongoCollections collections;
    private final Clock clock;

    MongoSchema(MongoCollections collections, Clock clock) {
        this.collections = collections;
        this.clock = clock;
    }

    /**
     * Cree les index manquants et enregistre la version.
     *
     * @return la version de schema de la base
     * @throws StorageException si la base porte un schema plus recent
     */
    int migrate() {
        int current = currentVersion();
        if (current > VERSION) {
            throw new StorageException(
                    ConsoleText.tr("MongoDB : schema v{0} plus recent que celui de cette version de NovaBank (v{1}) : mettez NovaBank a jour",
                            current, VERSION));
        }
        collections.operations().createIndexes(List.of(
                new IndexModel(Indexes.compoundIndex(Indexes.ascending(OperationDocuments.BANK_ID),
                        Indexes.descending(OperationDocuments.AT_SECONDS, OperationDocuments.AT_NANOS,
                                OperationDocuments.ID)), new IndexOptions().name("history")),
                new IndexModel(Indexes.ascending(OperationDocuments.AT_SECONDS, OperationDocuments.AT_NANOS,
                        OperationDocuments.ID), new IndexOptions().name("reserved_withdrawals")
                        .partialFilterExpression(Filters.and(
                                Filters.eq(OperationDocuments.STATE, OperationState.RESERVED.name()),
                                Filters.eq(OperationDocuments.TYPE, OperationType.WITHDRAW.name()))))));
        collections.banks().createIndex(Indexes.ascending(PendingOperations.RECORD_ID_PATH),
                new IndexOptions().name("pending_operation")
                        .partialFilterExpression(Filters.exists(BankDocuments.PENDING)));
        collections.banks().createIndex(Indexes.ascending(BankDocuments.UPDATED_AT, BankDocuments.ID),
                new IndexOptions().name("bank_changes"));
        collections.deliveries().createIndex(Indexes.ascending(MongoPendingDeliveryStore.PLAYER,
                        MongoPendingDeliveryStore.CREATED_AT, "_id"), new IndexOptions().name("player_deliveries"));
        collections.schema().updateOne(Filters.eq("_id", SCHEMA_ID), Updates.combine(
                Updates.max(VERSION_FIELD, VERSION), Updates.set("updatedAt", new Date(clock.millis()))),
                new UpdateOptions().upsert(true));
        return currentVersion();
    }

    /**
     * @return la version enregistree, 0 pour une base vide
     */
    int currentVersion() {
        Document schema = collections.schema().find(Filters.eq("_id", SCHEMA_ID)).first();
        return schema == null ? 0 : schema.getInteger(VERSION_FIELD);
    }
}
