package dev.novadev.novabank.storage.mongo;

import com.mongodb.MongoException;
import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.TransactionOptions;
import com.mongodb.WriteConcern;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.model.Filters;
import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.api.OperationId;
import dev.novadev.novabank.api.operation.OperationStatus;
import dev.novadev.novabank.core.bank.Bank;
import dev.novadev.novabank.core.journal.OperationRecord;
import dev.novadev.novabank.core.repository.ApplyOutcome;
import dev.novadev.novabank.core.repository.BankMutation;
import dev.novadev.novabank.core.repository.MutationDecision;
import dev.novadev.novabank.core.repository.RecordTransition;
import dev.novadev.novabank.core.repository.RepositorySupport;
import dev.novadev.novabank.core.repository.StorageException;
import dev.novadev.novabank.core.text.ConsoleText;
import java.util.Optional;
import java.util.function.Function;
import org.bson.Document;
import org.bson.conversions.Bson;

/**
 * Ecritures en transaction multi-documents (replica set ou cluster shard) : lecture de la banque
 * et du journal, mutation pure, ecriture de la banque conditionnee sur la version lue et de
 * l'entree de journal, validation. Lectures en {@code snapshot}, validation {@code majority}
 * journalisee.
 *
 * <p>Identifiant journalise entre-temps, version changee ou conflit d'ecriture entre transactions
 * ({@code WriteConflict}) : la tentative est annulee et rejouee dans le budget de temps (conflit).
 * Autre erreur etiquetee {@code TransientTransactionError} (election, coupure reseau) : annulee et
 * rejouee un nombre limite de fois via {@link TransientFailure}. Validation au resultat inconnu
 * ({@code UnknownTransactionCommitResult}) : la validation seule est rejouee (idempotente cote
 * serveur) jusqu'a {@value #MAX_COMMIT_RETRIES} fois ; au-dela, l'erreur remonte en
 * {@link StorageException} et l'operation se tranche en rejouant son identifiant (rejeu detecte
 * par le journal).
 *
 * <p>Une operation en attente laissee par un fonctionnement autonome anterieur est d'abord resolue
 * ({@link PendingOperations}), hors transaction.
 */
final class TransactionalBankWrites implements BankWrites {

    private static final TransactionOptions OPTIONS = TransactionOptions.builder()
            .readConcern(ReadConcern.SNAPSHOT)
            .writeConcern(WriteConcern.MAJORITY.withJournal(true))
            .readPreference(ReadPreference.primary())
            .build();

    /** Code serveur d'un conflit d'ecriture entre transactions concurrentes. */
    private static final int WRITE_CONFLICT = 112;

    /** Rejeux maximaux d'une validation au resultat inconnu. */
    static final int MAX_COMMIT_RETRIES = 5;

    private final MongoClient client;
    private final MongoCollections collections;
    private final BankDocuments banks;
    private final PendingOperations pending;

    TransactionalBankWrites(MongoClient client, MongoCollections collections, BankDocuments banks,
                            PendingOperations pending) {
        this.client = client;
        this.collections = collections;
        this.banks = banks;
        this.pending = pending;
    }

    @Override
    public Optional<ApplyOutcome> applyOnce(OperationId operationId, BankId bankId, BankMutation mutation) {
        return inTransaction(session -> {
            Document document = collections.banks().find(session, byId(bankId)).first();
            if (document == null) {
                return Step.done(new ApplyOutcome.Rejected(OperationStatus.UNKNOWN_BANK));
            }
            if (PendingOperations.isPending(document)
                    || collections.banks().find(session, PendingOperations.byOperation(operationId)).first() != null) {
                return Step.resolvePending(operationId, document);
            }
            Optional<OperationRecord> existing = journalEntry(session, operationId);
            if (existing.isPresent()) {
                return Step.done(new ApplyOutcome.Duplicate(existing.get()));
            }
            Bank current = banks.fromDocument(document);
            MutationDecision decision = mutation.decide(current);
            if (!(decision instanceof MutationDecision.Accept accept)) {
                return Step.done(RepositorySupport.outcomeWithoutChange(decision, null));
            }
            RepositorySupport.requireSameTarget(accept.record(), operationId, bankId);
            Bank saved = RepositorySupport.successor(current, accept.bank());
            if (!save(session, current, saved)) {
                return Step.conflict();
            }
            try {
                collections.operations().insertOne(session, OperationDocuments.toDocument(accept.record(), saved.version()));
            } catch (MongoException failure) {
                if (MongoFailures.isDuplicateKey(failure)) {
                    // Journalise entre-temps par un autre serveur : la tentative suivante le verra.
                    return Step.conflict();
                }
                throw failure;
            }
            return Step.commit(new ApplyOutcome.Applied(saved, accept.record()));
        });
    }

    @Override
    public Optional<ApplyOutcome> settleOnce(OperationId operationId, RecordTransition transition) {
        return inTransaction(session -> {
            if (collections.banks().find(session, PendingOperations.byOperation(operationId)).first() != null) {
                return Step.resolvePending(operationId, null);
            }
            Optional<OperationRecord> known = journalEntry(session, operationId);
            if (known.isEmpty()) {
                return Step.done(new ApplyOutcome.Rejected(OperationStatus.UNKNOWN_OPERATION));
            }
            OperationRecord record = known.get();
            Document document = collections.banks().find(session, byId(record.bankId())).first();
            if (document == null) {
                throw new StorageException(ConsoleText.tr("MongoDB : banque de l'operation {0} introuvable : {1}",
                        operationId, record.bankId()));
            }
            if (PendingOperations.isPending(document)) {
                return Step.resolvePending(operationId, document);
            }
            Bank current = banks.fromDocument(document);
            MutationDecision decision = transition.decide(current, record);
            if (!(decision instanceof MutationDecision.Accept accept)) {
                return Step.done(RepositorySupport.outcomeWithoutChange(decision, record));
            }
            RepositorySupport.requireSameTarget(accept.record(), operationId, record.bankId());
            Bank saved = RepositorySupport.successor(current, accept.bank());
            if (!save(session, current, saved)) {
                return Step.conflict();
            }
            Document replacement = OperationDocuments.toDocument(accept.record(), saved.version());
            if (collections.operations().replaceOne(session, Filters.eq(OperationDocuments.ID, operationId.toString()),
                    replacement).getMatchedCount() != 1) {
                throw new StorageException(ConsoleText.tr("MongoDB : operation disparue du journal : {0}",
                        operationId));
            }
            return Step.commit(new ApplyOutcome.Applied(saved, accept.record()));
        });
    }

    /**
     * Execute une tentative en transaction. Toute sortie autre qu'une validation reussie annule la
     * transaction.
     */
    private Optional<ApplyOutcome> inTransaction(Function<ClientSession, Step> body) {
        try (ClientSession session = client.startSession()) {
            session.startTransaction(OPTIONS);
            Step step;
            try {
                step = body.apply(session);
                if (step.kind() != StepKind.COMMIT) {
                    session.abortTransaction();
                }
            } catch (MongoException failure) {
                abortQuietly(session, failure);
                if (failure.getCode() == WRITE_CONFLICT) {
                    // Contention sur la banque : conflit ordinaire, rejoue dans le budget de temps.
                    return Optional.empty();
                }
                if (failure.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)) {
                    throw new TransientFailure(failure);
                }
                throw failure;
            } catch (RuntimeException failure) {
                abortQuietly(session, failure);
                throw failure;
            }
            return switch (step.kind()) {
                case DONE -> Optional.of(step.outcome());
                case CONFLICT -> Optional.empty();
                case RESOLVE_PENDING -> {
                    resolvePending(step);
                    yield Optional.empty();
                }
                case COMMIT -> {
                    commit(session);
                    yield Optional.of(step.outcome());
                }
            };
        }
    }

    /**
     * Valide la transaction, en rejouant la seule validation si son resultat est inconnu.
     *
     * @throws TransientFailure si la transaction a echoue de facon transitoire (a rejouer en entier)
     */
    private static void commit(ClientSession session) {
        for (int retries = 0; ; retries++) {
            try {
                session.commitTransaction();
                return;
            } catch (MongoException failure) {
                if (failure.hasErrorLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL)
                        && retries < MAX_COMMIT_RETRIES) {
                    continue;
                }
                if (failure.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)) {
                    throw new TransientFailure(failure);
                }
                throw failure;
            }
        }
    }

    private void resolvePending(Step step) {
        if (step.pendingBank() != null && PendingOperations.isPending(step.pendingBank())) {
            pending.resolve(step.pendingBank());
        }
        pending.resolveFor(step.operationId());
    }

    private boolean save(ClientSession session, Bank current, Bank saved) {
        Bson filter = Filters.and(byId(current.id()), Filters.eq(BankDocuments.VERSION, current.version()),
                PendingOperations.notPending());
        return collections.banks().updateOne(session, filter, new Document("$set", banks.mutableFields(saved)))
                .getMatchedCount() == 1;
    }

    private Optional<OperationRecord> journalEntry(ClientSession session, OperationId operationId) {
        Document document = collections.operations()
                .find(session, Filters.eq(OperationDocuments.ID, operationId.toString())).first();
        return Optional.ofNullable(document).map(OperationDocuments::fromDocument);
    }

    private static Bson byId(BankId bankId) {
        return Filters.eq(BankDocuments.ID, bankId.toString());
    }

    private static void abortQuietly(ClientSession session, Exception failure) {
        if (!session.hasActiveTransaction()) {
            return;
        }
        try {
            session.abortTransaction();
        } catch (MongoException abortFailure) {
            failure.addSuppressed(abortFailure);
        }
    }

    private enum StepKind { DONE, CONFLICT, RESOLVE_PENDING, COMMIT }

    /**
     * Issue du corps d'une transaction.
     *
     * @param kind        suite a donner
     * @param outcome     issue a renvoyer (DONE, COMMIT)
     * @param operationId operation dont l'attente est a resoudre (RESOLVE_PENDING)
     * @param pendingBank banque lue, eventuellement en attente (RESOLVE_PENDING)
     */
    private record Step(StepKind kind, ApplyOutcome outcome, OperationId operationId, Document pendingBank) {

        static Step done(ApplyOutcome outcome) {
            return new Step(StepKind.DONE, outcome, null, null);
        }

        static Step commit(ApplyOutcome outcome) {
            return new Step(StepKind.COMMIT, outcome, null, null);
        }

        static Step conflict() {
            return new Step(StepKind.CONFLICT, null, null, null);
        }

        static Step resolvePending(OperationId operationId, Document pendingBank) {
            return new Step(StepKind.RESOLVE_PENDING, null, operationId, pendingBank);
        }
    }
}
