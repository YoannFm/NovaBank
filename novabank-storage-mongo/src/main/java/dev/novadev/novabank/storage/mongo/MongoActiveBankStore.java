package dev.novadev.novabank.storage.mongo;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.core.selection.ActiveBankStore;
import dev.novadev.novabank.core.storage.ActiveBankChoice;
import java.time.Clock;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import org.bson.Document;

/**
 * {@link ActiveBankStore} MongoDB : {@code { _id: "joueur", entityType, entityId, updatedAt }}.
 */
final class MongoActiveBankStore implements ActiveBankStore {

    private static final String ID = "_id";
    private static final String ENTITY_TYPE = "entityType";
    private static final String ENTITY_ID = "entityId";
    private static final String UPDATED_AT = "updatedAt";

    private final MongoCollections collections;
    private final Clock clock;
    private final MongoFailures failures;

    MongoActiveBankStore(MongoCollections collections, Clock clock, MongoFailures failures) {
        this.collections = collections;
        this.clock = clock;
        this.failures = failures;
    }

    @Override
    public Optional<EntityRef> find(UUID playerId) {
        return failures.call("lecture de la banque active du joueur " + playerId, () -> Optional.ofNullable(
                collections.activeBanks().find(Filters.eq(ID, playerId.toString())).first())
                .map(document -> fromDocument(document).entity()));
    }

    @Override
    public void save(UUID playerId, EntityRef entity) {
        failures.call("enregistrement de la banque active du joueur " + playerId, () -> collections.activeBanks()
                .replaceOne(Filters.eq(ID, playerId.toString()), toDocument(new ActiveBankChoice(playerId, entity),
                        clock.millis()), new ReplaceOptions().upsert(true)));
    }

    /**
     * @param choice    banque active d'un joueur
     * @param updatedAt date de l'enregistrement, en millisecondes
     * @return son document
     */
    static Document toDocument(ActiveBankChoice choice, long updatedAt) {
        return new Document(ID, choice.playerId().toString()).append(ENTITY_TYPE, choice.entity().type())
                .append(ENTITY_ID, choice.entity().id()).append(UPDATED_AT, new Date(updatedAt));
    }

    /**
     * @param document document d'un choix
     * @return le choix
     */
    static ActiveBankChoice fromDocument(Document document) {
        return new ActiveBankChoice(UUID.fromString(document.getString(ID)),
                new EntityRef(document.getString(ENTITY_TYPE), document.getString(ENTITY_ID)));
    }
}
