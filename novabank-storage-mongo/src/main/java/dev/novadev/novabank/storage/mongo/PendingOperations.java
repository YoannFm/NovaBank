package dev.novadev.novabank.storage.mongo;

import com.mongodb.MongoException;
import com.mongodb.WriteConcern;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import dev.novadev.novabank.api.OperationId;
import dev.novadev.novabank.core.bank.Bank;
import dev.novadev.novabank.core.journal.OperationRecord;
import dev.novadev.novabank.core.repository.StorageException;
import dev.novadev.novabank.core.text.ConsoleText;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.bson.Document;
import org.bson.conversions.Bson;

/**
 * Protocole du journal en attente : atomicite sans transaction multi-documents (MongoDB autonome).
 *
 * <h2>Ecriture d'une operation</h2>
 * <ol>
 *   <li>Mise a jour atomique du seul document banque, conditionnee sur sa version lue et sur
 *       l'absence d'operation en attente : nouvel etat (version + 1) ET entree {@code pendingOp}
 *       qui contient l'entree de journal, sa nature (insertion ou remplacement), la version
 *       ecrite et l'etat precedent (lignes, epoch). C'est le point de validation : une seule
 *       ecriture d'un seul document, donc tout ou rien.</li>
 *   <li>Propagation : insertion (ou remplacement) idempotente de l'entree dans {@code operations},
 *       dont l'index unique sur {@code _id} est l'arbitre final de l'unicite d'un identifiant
 *       d'operation toutes banques confondues. Un remplacement est conditionne sur la version de
 *       banque portee par l'entree du journal ({@link OperationDocuments#BANK_VERSION}) : un
 *       resolveur en retard ne peut jamais ecraser une evolution plus recente de la meme
 *       operation.</li>
 *   <li>Retrait de {@code pendingOp}, conditionne sur la version ecrite.</li>
 * </ol>
 *
 * <p>Les etapes 1 et 2 (et une annulation) sont ecrites avec la garantie durable du backend.
 * L'etape 3 est seulement acquittee par le primaire ({@code w: 1}, sans attente du journal) :
 * sa perte sur arret brutal ne fait que laisser l'attente, de nouveau resolue (sans effet) par la
 * lecture suivante ou au demarrage, et les ecritures d'un serveur sont journalisees dans l'ordre,
 * si bien qu'elle ne peut pas survivre sans les etapes 1 et 2. Une attente durable de moins par
 * operation : c'est le cout dominant sous contention.
 *
 * <h2>Rattrapage</h2>
 * Une banque qui porte une operation en attente est gelee : aucune autre mutation ne peut s'y
 * appliquer avant sa resolution (filtre {@code pendingOp} absent). Toute lecture d'une banque,
 * d'une operation, du journal ou des retraits reserves resout d'abord les operations en attente
 * concernees, et le demarrage les resout toutes : un arret entre les etapes 1 et 3 est rattrape
 * sans perte ni double effet, et aucune lecture ne voit une operation a moitie appliquee. Une
 * attente rencontree en cours de route est d'abord laissee quelques millisecondes a son ecrivain
 * ({@link #awaitOrResolve}) : sous contention, les autres clients n'ajoutent pas d'ecritures
 * redondantes ; une attente orpheline (ecrivain arrete) est resolue a sa place.
 *
 * <h2>Cas residuel</h2>
 * Le meme identifiant soumis en meme temps a deux banques differentes (erreur de l'appelant :
 * les identifiants sont aleatoires) peut etre valide sur les deux documents banque avant que
 * l'un ne gagne l'insertion dans {@code operations}. Le perdant est alors annule : son etat
 * precedent est restaure (avec une version de plus, la version ne recule jamais), l'appelant
 * perdant recoit {@code Duplicate} et un avertissement est journalise. Effet net : une seule
 * application. Les lectures passant par ce depot resolvent l'attente avant de lire et ne voient
 * donc jamais l'effet du perdant ; seule une lecture directe de la base pendant cette fenetre le
 * verrait.
 */
final class PendingOperations {

    /** Nature de l'entree de journal en attente. */
    enum Kind {
        /** Operation nouvelle : insertion dans {@code operations}. */
        INSERT,
        /** Operation existante qui evolue (solde de retrait) : remplacement dans {@code operations}. */
        REPLACE
    }

    /** Issue d'une resolution. */
    enum Resolution {
        /** L'entree est (ou etait deja) dans le journal ; l'attente est levee. */
        PROPAGATED,
        /** L'identifiant appartient a une autre banque : l'etat precedent est restaure. */
        UNDONE
    }

    /** Chemin de l'identifiant d'operation en attente (index {@code pending_operation}). */
    static final String RECORD_ID_PATH = BankDocuments.PENDING + ".record._id";

    private static final String KIND = "kind";
    private static final String RECORD = "record";
    private static final String VERSION = "version";
    private static final String PREVIOUS = "previous";
    private static final String VERSION_PATH = BankDocuments.PENDING + "." + VERSION;

    /** Pauses successives laissees a l'ecrivain d'une attente avant de la resoudre a sa place. */
    private static final long[] WRITER_GRACE_MS = {1L, 2L, 4L, 8L, 16L, 32L};

    private final MongoCollections collections;
    private final BankDocuments banks;
    private final Clock clock;
    private final Logger logger;

    PendingOperations(MongoCollections collections, BankDocuments banks, Clock clock, Logger logger) {
        this.collections = collections;
        this.banks = banks;
        this.clock = clock;
        this.logger = logger;
    }

    /**
     * @param kind     nature de l'ecriture dans le journal
     * @param record   entree de journal a propager
     * @param previous etat de la banque avant la mutation (restaure en cas d'annulation)
     * @param saved    etat enregistre (version ecrite)
     * @return l'entree {@code pendingOp} a ecrire dans le document banque
     */
    Document entry(Kind kind, OperationRecord record, Bank previous, Bank saved) {
        return new Document(KIND, kind.name())
                .append(RECORD, OperationDocuments.toDocument(record, saved.version()))
                .append(VERSION, saved.version())
                .append(PREVIOUS, new Document(BankDocuments.LINES, banks.lines(previous))
                        .append(BankDocuments.RESET_EPOCH, previous.resetEpoch()));
    }

    /**
     * @param entry entree en attente ({@link #entry})
     * @param saved etat enregistre
     * @return la mise a jour qui ecrit l'etat et l'attente dans le document banque
     */
    Bson validation(Document entry, Bank saved) {
        return new Document("$set", banks.mutableFields(saved).append(BankDocuments.PENDING, entry));
    }

    /**
     * @param bank banque lue
     * @return {@code true} si elle porte une operation en attente
     */
    static boolean isPending(Document bank) {
        return bank.containsKey(BankDocuments.PENDING);
    }

    /**
     * @return le filtre des banques sans operation en attente
     */
    static Bson notPending() {
        return Filters.exists(BankDocuments.PENDING, false);
    }

    /**
     * @param operationId operation
     * @return le filtre des banques portant cette operation en attente (index partiel
     *         {@code pending_operation})
     */
    static Bson byOperation(OperationId operationId) {
        return Filters.and(Filters.exists(BankDocuments.PENDING), Filters.eq(RECORD_ID_PATH, operationId.toString()));
    }

    /**
     * Laisse a l'ecrivain d'une attente rencontree le temps de la resoudre lui-meme (quelques
     * relectures espacees, sans ecriture), puis la resout a sa place si elle est toujours la : cas
     * d'un ecrivain arrete entre les etapes du protocole.
     *
     * @param bank document banque lu, portant {@code pendingOp}
     * @throws StorageException si le thread est interrompu pendant l'attente
     */
    void awaitOrResolve(Document bank) {
        String bankId = bank.getString(BankDocuments.ID);
        Document entry = bank.get(BankDocuments.PENDING, Document.class);
        Bson samePending = Filters.and(Filters.eq(BankDocuments.ID, bankId),
                Filters.eq(VERSION_PATH, entry.getLong(VERSION)));
        for (long pause : WRITER_GRACE_MS) {
            sleep(pause);
            if (collections.banks().find(samePending).projection(Projections.include(BankDocuments.ID)).first()
                    == null) {
                return;
            }
        }
        resolve(bankId, entry);
    }

    /**
     * Resout l'operation en attente d'une banque lue (propagation ou annulation). Idempotent et
     * sur en concurrence : plusieurs resolveurs simultanes aboutissent au meme etat.
     *
     * @param bank document banque portant {@code pendingOp}
     * @return l'issue de la resolution
     */
    Resolution resolve(Document bank) {
        return resolve(bank.getString(BankDocuments.ID), bank.get(BankDocuments.PENDING, Document.class));
    }

    /**
     * Resout une attente connue de l'appelant (celle qu'il vient d'ecrire), qu'elle soit encore
     * presente ou deja resolue par un lecteur concurrent : meme issue dans les deux cas.
     *
     * @param bankId  banque
     * @param pending entree {@code pendingOp} ecrite
     * @return l'issue de la resolution
     * @throws MongoException si la base est injoignable (l'attente reste, resolue plus tard)
     */
    Resolution resolve(String bankId, Document pending) {
        long version = pending.getLong(VERSION);
        Document record = pending.get(RECORD, Document.class);
        Bson ownPending = Filters.and(Filters.eq(BankDocuments.ID, bankId), Filters.eq(VERSION_PATH, version));
        if (propagate(Kind.valueOf(pending.getString(KIND)), bankId, record, version)) {
            collections.banks().withWriteConcern(WriteConcern.W1).updateOne(ownPending,
                    Updates.unset(BankDocuments.PENDING));
            return Resolution.PROPAGATED;
        }
        Document previous = pending.get(PREVIOUS, Document.class);
        long undone = collections.banks().updateOne(ownPending, Updates.combine(
                Updates.set(BankDocuments.LINES, previous.getList(BankDocuments.LINES, Document.class)),
                Updates.set(BankDocuments.RESET_EPOCH, previous.getLong(BankDocuments.RESET_EPOCH)),
                Updates.set(BankDocuments.VERSION, Math.addExact(version, 1L)),
                Updates.set(BankDocuments.UPDATED_AT, new Date(clock.millis())),
                Updates.unset(BankDocuments.PENDING))).getModifiedCount();
        if (undone == 1) {
            // Une seule fois : un resolveur concurrent arrive apres l'annulation ne modifie rien.
            logger.warning(
                    ConsoleText.tr("MongoDB : operation {0} annulee sur la banque {1} (identifiant deja journalise pour une autre banque)",
                            record.getString(OperationDocuments.ID), bankId));
        }
        return Resolution.UNDONE;
    }

    /**
     * Resout les operations en attente d'un identifiant donne, toutes banques confondues.
     *
     * @param operationId operation
     * @return le nombre d'attentes resolues
     */
    int resolveFor(OperationId operationId) {
        return resolveAll(byOperation(operationId));
    }

    /**
     * Rattrapage : resout toutes les operations en attente (demarrage, liste des retraits reserves).
     *
     * @return le nombre d'attentes resolues
     */
    int resolveAll() {
        return resolveAll(Filters.exists(BankDocuments.PENDING));
    }

    private int resolveAll(Bson filter) {
        List<Document> pendingBanks = collections.banks().find(filter).into(new ArrayList<>());
        for (Document bank : pendingBanks) {
            resolve(bank);
        }
        return pendingBanks.size();
    }

    private static void sleep(long millis) {
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new StorageException(
                    ConsoleText.tr("MongoDB : attente d'une operation en cours interrompue"), interrupted);
        }
    }

    /**
     * @param version version de banque ecrite avec l'entree
     * @return {@code true} si l'entree appartient bien a cette banque dans le journal
     */
    private boolean propagate(Kind kind, String bankId, Document record, long version) {
        Bson sameOperation = Filters.and(Filters.eq(OperationDocuments.ID, record.getString(OperationDocuments.ID)),
                Filters.eq(OperationDocuments.BANK_ID, bankId));
        if (kind == Kind.REPLACE) {
            // Jamais par-dessus une entree ecrite avec une version de banque egale ou plus recente
            // (resolveur en retard) ; une entree sans version (ecrite anterieurement) est remplacee.
            if (collections.operations().replaceOne(Filters.and(sameOperation,
                    Filters.not(Filters.gte(OperationDocuments.BANK_VERSION, version))), record).getMatchedCount() == 1) {
                return true;
            }
            return collections.operations().find(sameOperation).projection(Projections.include(OperationDocuments.ID))
                    .first() != null;
        }
        try {
            collections.operations().insertOne(record);
            return true;
        } catch (MongoException failure) {
            if (!MongoFailures.isDuplicateKey(failure)) {
                throw failure;
            }
            Document existing = collections.operations()
                    .find(Filters.eq(OperationDocuments.ID, record.getString(OperationDocuments.ID))).first();
            if (existing == null) {
                throw new StorageException(ConsoleText.tr("MongoDB : operation {0} signalee en double mais introuvable",
                        record.getString(OperationDocuments.ID)), failure);
            }
            return bankId.equals(existing.getString(OperationDocuments.BANK_ID));
        }
    }
}
