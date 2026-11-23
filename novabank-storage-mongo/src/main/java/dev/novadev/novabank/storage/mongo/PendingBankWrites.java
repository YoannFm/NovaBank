package dev.novadev.novabank.storage.mongo;

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
import org.bson.Document;
import org.bson.conversions.Bson;

/**
 * Ecritures sans transaction (MongoDB autonome), selon le protocole de {@link PendingOperations}.
 *
 * <p>Ordre des lectures d'une tentative : banque d'abord (attente eventuelle resolue puis tentative
 * rejouee), journal ensuite. Ainsi une operation concurrente deja validee sur cette banque est
 * vue soit dans l'attente de la banque, soit dans le journal (elle n'y quitte l'attente qu'apres
 * y avoir ete inscrite), et la mise a jour conditionnee sur la version lue echoue si la banque a
 * change entre-temps.
 */
final class PendingBankWrites implements BankWrites {

    private final MongoCollections collections;
    private final BankDocuments banks;
    private final PendingOperations pending;

    PendingBankWrites(MongoCollections collections, BankDocuments banks, PendingOperations pending) {
        this.collections = collections;
        this.banks = banks;
        this.pending = pending;
    }

    @Override
    public Optional<ApplyOutcome> applyOnce(OperationId operationId, BankId bankId, BankMutation mutation) {
        Document document = collections.banks().find(Filters.eq(BankDocuments.ID, bankId.toString())).first();
        if (document == null) {
            return Optional.of(new ApplyOutcome.Rejected(OperationStatus.UNKNOWN_BANK));
        }
        if (PendingOperations.isPending(document)) {
            pending.awaitOrResolve(document);
            return Optional.empty();
        }
        Optional<OperationRecord> existing = journalEntry(operationId);
        if (existing.isPresent()) {
            return Optional.of(new ApplyOutcome.Duplicate(existing.get()));
        }
        if (pending.resolveFor(operationId) > 0) {
            // Identifiant valide sur une autre banque mais pas encore propage : rejouer.
            return Optional.empty();
        }
        Bank current = banks.fromDocument(document);
        MutationDecision decision = mutation.decide(current);
        if (!(decision instanceof MutationDecision.Accept accept)) {
            return Optional.of(RepositorySupport.outcomeWithoutChange(decision, null));
        }
        RepositorySupport.requireSameTarget(accept.record(), operationId, bankId);
        Bank saved = RepositorySupport.successor(current, accept.bank());
        Document entry = pending.entry(PendingOperations.Kind.INSERT, accept.record(), current, saved);
        if (!validate(entry, current, saved)) {
            return Optional.empty();
        }
        if (pending.resolve(bankId.toString(), entry) == PendingOperations.Resolution.UNDONE) {
            return Optional.of(new ApplyOutcome.Duplicate(journalEntry(operationId)
                    .orElseThrow(() -> new StorageException(
                            ConsoleText.tr("MongoDB : operation {0} annulee mais absente du journal",
                                    operationId)))));
        }
        return Optional.of(new ApplyOutcome.Applied(saved, accept.record()));
    }

    @Override
    public Optional<ApplyOutcome> settleOnce(OperationId operationId, RecordTransition transition) {
        if (pending.resolveFor(operationId) > 0) {
            return Optional.empty();
        }
        Optional<OperationRecord> known = journalEntry(operationId);
        if (known.isEmpty()) {
            return Optional.of(new ApplyOutcome.Rejected(OperationStatus.UNKNOWN_OPERATION));
        }
        BankId bankId = known.get().bankId();
        Document document = collections.banks().find(Filters.eq(BankDocuments.ID, bankId.toString())).first();
        if (document == null) {
            throw new StorageException(ConsoleText.tr("MongoDB : banque de l'operation {0} introuvable : {1}",
                    operationId, bankId));
        }
        if (PendingOperations.isPending(document)) {
            pending.awaitOrResolve(document);
            return Optional.empty();
        }
        // Relue APRES la banque : une evolution concurrente deja validee a change la version lue.
        OperationRecord record = journalEntry(operationId)
                .orElseThrow(() -> new StorageException(ConsoleText.tr("MongoDB : operation disparue du journal : {0}",
                        operationId)));
        Bank current = banks.fromDocument(document);
        MutationDecision decision = transition.decide(current, record);
        if (!(decision instanceof MutationDecision.Accept accept)) {
            return Optional.of(RepositorySupport.outcomeWithoutChange(decision, record));
        }
        RepositorySupport.requireSameTarget(accept.record(), operationId, bankId);
        Bank saved = RepositorySupport.successor(current, accept.bank());
        Document entry = pending.entry(PendingOperations.Kind.REPLACE, accept.record(), current, saved);
        if (!validate(entry, current, saved)) {
            return Optional.empty();
        }
        if (pending.resolve(bankId.toString(), entry) == PendingOperations.Resolution.UNDONE) {
            throw new StorageException(
                    ConsoleText.tr("MongoDB : evolution de l'operation {0} annulee (journal incoherent)",
                            operationId));
        }
        return Optional.of(new ApplyOutcome.Applied(saved, accept.record()));
    }

    /**
     * Etape 1 : ecriture atomique de l'etat et de l'attente, conditionnee sur la version lue. Les
     * etapes 2 et 3 ({@link PendingOperations#resolve(String, Document)}) suivent ; une panne entre
     * les deux laisse l'attente, resolue par la prochaine lecture ou au demarrage.
     *
     * @return {@code false} si la banque a change entre-temps (conflit)
     */
    private boolean validate(Document entry, Bank current, Bank saved) {
        Bson filter = Filters.and(Filters.eq(BankDocuments.ID, current.id().toString()),
                Filters.eq(BankDocuments.VERSION, current.version()), PendingOperations.notPending());
        return collections.banks().updateOne(filter, pending.validation(entry, saved)).getMatchedCount() == 1;
    }

    private Optional<OperationRecord> journalEntry(OperationId operationId) {
        Document document = collections.operations().find(Filters.eq(OperationDocuments.ID, operationId.toString()))
                .first();
        return Optional.ofNullable(document).map(OperationDocuments::fromDocument);
    }
}
