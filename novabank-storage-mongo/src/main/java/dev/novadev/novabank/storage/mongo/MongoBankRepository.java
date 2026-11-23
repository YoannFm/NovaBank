package dev.novadev.novabank.storage.mongo;

import com.mongodb.MongoException;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UpdateOptions;
import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.api.OperationId;
import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.operation.OperationStatus;
import dev.novadev.novabank.api.operation.OperationType;
import dev.novadev.novabank.api.valuation.ValuationMode;
import dev.novadev.novabank.core.bank.Bank;
import dev.novadev.novabank.core.journal.OperationRecord;
import dev.novadev.novabank.core.journal.OperationState;
import dev.novadev.novabank.core.repository.ApplyOutcome;
import dev.novadev.novabank.core.repository.BankMutation;
import dev.novadev.novabank.core.repository.BankRepository;
import dev.novadev.novabank.core.repository.RecordTransition;
import dev.novadev.novabank.core.repository.RepositorySupport;
import dev.novadev.novabank.core.repository.StorageException;
import dev.novadev.novabank.core.sync.BankChange;
import dev.novadev.novabank.core.sync.BankChangeFeed;
import dev.novadev.novabank.core.text.ConsoleText;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bson.Document;
import org.bson.conversions.Bson;

/**
 * {@link BankRepository} MongoDB. Les ecritures passent par la strategie choisie a l'ouverture
 * ({@link BankWrites}) et sont rejouees sur conflit dans le budget de {@link Contention} ; au-dela,
 * {@link OperationStatus#CONCURRENT_MODIFICATION} sans rien avoir ecrit. Toute lecture resout
 * d'abord les operations en attente qui la concernent ({@link PendingOperations}). Toute erreur du
 * pilote remonte en {@link StorageException} (URI et mot de passe masques).
 */
final class MongoBankRepository implements BankRepository, BankChangeFeed {

    /** Nombre maximal de resolutions d'attente successives pendant une lecture. */
    private static final int MAX_READ_RESOLUTIONS = 100;

    private final MongoCollections collections;
    private final BankDocuments banks;
    private final PendingOperations pending;
    private final BankWrites writes;
    private final Contention contention;
    private final MongoFailures failures;

    MongoBankRepository(MongoCollections collections, BankDocuments banks, PendingOperations pending, BankWrites writes,
                        Contention contention, MongoFailures failures) {
        this.collections = collections;
        this.banks = banks;
        this.pending = pending;
        this.writes = writes;
        this.contention = contention;
        this.failures = failures;
    }

    @Override
    public Optional<Bank> findBank(BankId bankId) {
        return failures.call("lecture de la banque " + bankId, () -> readResolved(bankId).map(banks::fromDocument));
    }

    @Override
    public Bank createBank(BankId bankId, EntityRef owner, ValuationMode valuationMode) {
        return failures.call("creation de la banque " + bankId, () -> {
            try {
                collections.banks().updateOne(Filters.eq(BankDocuments.ID, bankId.toString()),
                        new Document("$setOnInsert", banks.toDocument(Bank.open(bankId, owner, valuationMode))),
                        new UpdateOptions().upsert(true));
            } catch (MongoException failure) {
                // Insertion concurrente du meme identifiant : la banque existe, elle est relue.
                if (!MongoFailures.isDuplicateKey(failure)) {
                    throw failure;
                }
            }
            return readResolved(bankId).map(banks::fromDocument)
                    .orElseThrow(() -> new StorageException(
                            ConsoleText.tr("MongoDB : banque introuvable apres creation : {0}",
                                    bankId)));
        });
    }

    @Override
    public Optional<OperationRecord> findOperation(OperationId operationId) {
        return failures.call("lecture de l'operation " + operationId, () -> {
            pending.resolveFor(operationId);
            return Optional.ofNullable(collections.operations()
                    .find(Filters.eq(OperationDocuments.ID, operationId.toString())).first())
                    .map(OperationDocuments::fromDocument);
        });
    }

    @Override
    public ApplyOutcome apply(OperationId operationId, BankId bankId, BankMutation mutation) {
        return failures.call("operation " + operationId, () -> contention.retry(
                () -> writes.applyOnce(operationId, bankId, mutation)).orElseGet(MongoBankRepository::conflict));
    }

    @Override
    public ApplyOutcome settle(OperationId operationId, RecordTransition transition) {
        return failures.call("solde de l'operation " + operationId, () -> contention.retry(
                () -> writes.settleOnce(operationId, transition)).orElseGet(MongoBankRepository::conflict));
    }

    @Override
    public List<OperationRecord> reservedWithdrawals() {
        return failures.call("liste des retraits reserves", () -> {
            pending.resolveAll();
            Bson reserved = Filters.and(Filters.eq(OperationDocuments.STATE, OperationState.RESERVED.name()),
                    Filters.eq(OperationDocuments.TYPE, OperationType.WITHDRAW.name()));
            return records(reserved, Sorts.ascending(OperationDocuments.AT_SECONDS, OperationDocuments.AT_NANOS,
                    OperationDocuments.ID), 0, 0);
        });
    }

    @Override
    public List<OperationRecord> history(BankId bankId, int offset, int limit) {
        RepositorySupport.requirePage(offset, limit);
        return failures.call("journal de la banque " + bankId, () -> {
            readResolved(bankId);
            return records(Filters.eq(OperationDocuments.BANK_ID, bankId.toString()),
                    Sorts.descending(OperationDocuments.AT_SECONDS, OperationDocuments.AT_NANOS, OperationDocuments.ID),
                    offset, limit);
        });
    }

    @Override
    public List<BankChange> changesSince(Instant since, int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException(ConsoleText.tr("limit doit etre strictement positif : {0}", limit));
        }
        return failures.call("flux de changements des banques", () -> {
            List<BankChange> changes = new ArrayList<>();
            // La version d'une banque en attente (serveur autonome) est deja incrementee : la lecture
            // d'un autre serveur resout l'attente avant de rendre l'etat.
            collections.banks().find(Filters.gte(BankDocuments.UPDATED_AT, Date.from(since)))
                    .projection(new Document(BankDocuments.VERSION, 1).append(BankDocuments.RESET_EPOCH, 1)
                            .append(BankDocuments.UPDATED_AT, 1))
                    .sort(Sorts.ascending(BankDocuments.UPDATED_AT, BankDocuments.ID)).limit(limit)
                    .forEach(document -> changes.add(new BankChange(
                            new BankId(UUID.fromString(document.getString(BankDocuments.ID))),
                            document.getLong(BankDocuments.VERSION), document.getLong(BankDocuments.RESET_EPOCH),
                            document.getDate(BankDocuments.UPDATED_AT).toInstant())));
            return changes;
        });
    }

    /**
     * Rattrapage du demarrage : resout toutes les operations en attente.
     *
     * @return le nombre d'attentes resolues
     */
    int recover() {
        return failures.call("rattrapage des operations en attente", pending::resolveAll);
    }

    /**
     * @return le document de la banque, sans operation en attente
     */
    private Optional<Document> readResolved(BankId bankId) {
        Bson filter = Filters.eq(BankDocuments.ID, bankId.toString());
        for (int resolutions = 0; resolutions < MAX_READ_RESOLUTIONS; resolutions++) {
            Document document = collections.banks().find(filter).first();
            if (document == null || !PendingOperations.isPending(document)) {
                return Optional.ofNullable(document);
            }
            pending.awaitOrResolve(document);
        }
        throw new StorageException(ConsoleText.tr("MongoDB : banque {0} sans cesse modifiee pendant la lecture",
                bankId));
    }

    private List<OperationRecord> records(Bson filter, Bson sort, int offset, int limit) {
        List<OperationRecord> records = new ArrayList<>();
        for (Document document : collections.operations().find(filter).sort(sort).skip(offset).limit(limit)) {
            records.add(OperationDocuments.fromDocument(document));
        }
        return records;
    }


    private static ApplyOutcome conflict() {
        return new ApplyOutcome.Rejected(OperationStatus.CONCURRENT_MODIFICATION);
    }
}
