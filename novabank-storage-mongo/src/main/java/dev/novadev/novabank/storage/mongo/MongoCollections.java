package dev.novadev.novabank.storage.mongo;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

/**
 * Collections du stockage, dans la base configuree.
 *
 * @param banks       un document par banque
 * @param operations  journal des operations
 * @param deliveries  livraisons en attente
 * @param activeBanks banque active de chaque joueur
 * @param schema      version du schema
 */
record MongoCollections(MongoCollection<Document> banks, MongoCollection<Document> operations,
                        MongoCollection<Document> deliveries, MongoCollection<Document> activeBanks,
                        MongoCollection<Document> schema) {

    /**
     * @param database base configuree
     * @return ses collections NovaBank
     */
    static MongoCollections of(MongoDatabase database) {
        return new MongoCollections(database.getCollection("banks"), database.getCollection("operations"),
                database.getCollection("pending_deliveries"), database.getCollection("active_banks"),
                database.getCollection("schema"));
    }
}
