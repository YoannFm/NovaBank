package dev.novadev.novabank.storage.mongo;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Sorts;
import dev.novadev.novabank.core.bank.Bank;
import dev.novadev.novabank.core.journal.OperationRecord;
import dev.novadev.novabank.core.repository.StorageException;
import dev.novadev.novabank.core.storage.ActiveBankChoice;
import dev.novadev.novabank.core.storage.StorageContent;
import dev.novadev.novabank.core.storage.StoredDelivery;
import dev.novadev.novabank.core.text.ConsoleText;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import org.bson.Document;

/**
 * {@link StorageContent} MongoDB. Avant de lire les banques, les operations en attente (serveur
 * autonome) sont resolues ; une banque encore en attente pendant la lecture fait echouer
 * l'export (copie incoherente impossible). Chaque lot importe est un {@code insertMany}
 * acquitte selon la preoccupation d'ecriture durable du backend.
 */
final class MongoContent implements StorageContent {

    /** Version de banque des entrees importees : toute evolution ulterieure la depasse. */
    private static final long IMPORTED_BANK_VERSION = 0L;

    private final MongoCollections collections;
    private final BankDocuments banks;
    private final MongoBankRepository repository;
    private final MongoFailures failures;
    private final Clock clock;

    MongoContent(MongoCollections collections, BankDocuments banks, MongoBankRepository repository,
                 MongoFailures failures, Clock clock) {
        this.collections = collections;
        this.banks = banks;
        this.repository = repository;
        this.failures = failures;
        this.clock = clock;
    }

    @Override
    public boolean isEmpty() {
        return failures.call("verification d'une base vide", () -> List.of(collections.banks(),
                collections.operations(), collections.deliveries(), collections.activeBanks()).stream()
                .allMatch(collection -> collection.find().limit(1).first() == null));
    }

    @Override
    public void exportBanks(Consumer<Bank> sink) {
        repository.recover();
        export("lecture des banques", collections.banks(), document -> {
            if (PendingOperations.isPending(document)) {
                throw new StorageException(
                        ConsoleText.tr("MongoDB : banque {0} modifiee pendant la lecture (operation en attente)",
                                document.getString(BankDocuments.ID)));
            }
            return banks.fromDocument(document);
        }, sink);
    }

    @Override
    public void exportOperations(Consumer<OperationRecord> sink) {
        export("lecture du journal", collections.operations(), OperationDocuments::fromDocument, sink);
    }

    @Override
    public void exportDeliveries(Consumer<StoredDelivery> sink) {
        failures.call("lecture des livraisons", () -> {
            for (Document document : collections.deliveries().find()
                    .sort(Sorts.ascending(MongoPendingDeliveryStore.CREATED_AT, BankDocuments.ID))) {
                sink.accept(MongoPendingDeliveryStore.storedFromDocument(document));
            }
            return null;
        });
    }

    @Override
    public void exportActiveBanks(Consumer<ActiveBankChoice> sink) {
        export("lecture des banques actives", collections.activeBanks(), MongoActiveBankStore::fromDocument, sink);
    }

    @Override
    public void importBanks(List<Bank> imported) {
        insert("import des banques", collections.banks(), imported, banks::toDocument);
    }

    @Override
    public void importOperations(List<OperationRecord> records) {
        insert("import du journal", collections.operations(), records,
                record -> OperationDocuments.toDocument(record, IMPORTED_BANK_VERSION));
    }

    @Override
    public void importDeliveries(List<StoredDelivery> deliveries) {
        insert("import des livraisons", collections.deliveries(), deliveries, MongoPendingDeliveryStore::toDocument);
    }

    @Override
    public void importActiveBanks(List<ActiveBankChoice> choices) {
        long now = clock.millis();
        insert("import des banques actives", collections.activeBanks(), choices,
                choice -> MongoActiveBankStore.toDocument(choice, now));
    }

    private <T> void export(String action, MongoCollection<Document> collection, Function<Document, T> reader,
                            Consumer<T> sink) {
        failures.call(action, () -> {
            for (Document document : collection.find()) {
                sink.accept(reader.apply(document));
            }
            return null;
        });
    }

    private <T> void insert(String action, MongoCollection<Document> collection, List<T> elements,
                            Function<T, Document> writer) {
        if (elements.isEmpty()) {
            return;
        }
        List<Document> documents = new ArrayList<>(elements.size());
        elements.forEach(element -> documents.add(writer.apply(element)));
        failures.call(action, () -> collection.insertMany(documents));
    }
}
