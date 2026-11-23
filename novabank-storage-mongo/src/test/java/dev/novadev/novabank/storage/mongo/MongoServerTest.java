package dev.novadev.novabank.storage.mongo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.api.OperationId;
import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.api.valuation.ValuationMode;
import dev.novadev.novabank.core.bank.Bank;
import dev.novadev.novabank.core.bank.StockLine;
import dev.novadev.novabank.core.journal.OperationRecord;
import dev.novadev.novabank.core.journal.OperationState;
import dev.novadev.novabank.core.repository.ApplyOutcome;
import dev.novadev.novabank.core.repository.MutationDecision;
import dev.novadev.novabank.core.repository.StorageException;
import dev.novadev.novabank.core.testing.ConcurrentRunner;
import dev.novadev.novabank.core.testing.LedgerConcurrencyScenario;
import dev.novadev.novabank.core.testing.TestBankIds;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Comportements propres a MongoDB sur un vrai serveur : montants exacts au-dela de Decimal128,
 * detection du mode, concurrence multi-clients, rattrapage des operations en attente.
 */
@RequiresMongoServer
class MongoServerTest {

    private static final ItemKey STONE = ItemKey.minecraft("stone");
    private static final EntityRef OWNER = new EntityRef(EntityRef.PLAYER, "owner");
    private static final Instant AT = Instant.parse("2026-10-27T12:00:00.123456789Z");

    private MongoTestServer server;

    @BeforeEach
    void createDatabase() {
        server = MongoTestServer.create();
    }

    @AfterEach
    void dropDatabase() {
        server.close();
    }

    @Test
    void la_strategie_suit_la_nature_du_serveur() {
        try (MongoClient client = MongoTestServer.rawClient()) {
            ServerTopology topology = ServerTopology.detect(client.getDatabase("admin"));
            assertThat(topology.supportsTransactions()).isEqualTo(MongoTestServer.replicaSet());
            assertThat(topology.version()).matches("(8|9)\\..*");
        }
    }

    @Test
    void les_montants_sont_relus_exactement_meme_au_dela_de_34_chiffres() {
        MongoBackend backend = server.open();
        BankId bankId = TestBankIds.random();
        backend.banks().createBank(bankId, OWNER, ValuationMode.ACCUMULATED);
        BigDecimal enormous = new BigDecimal("1234567890123456789012345678901234567890.0001");
        OperationId id = OperationId.random();

        backend.banks().apply(id, bankId, bank -> MutationDecision.accept(
                bank.withLine(STONE, new StockLine(Long.MAX_VALUE, 1L, enormous)),
                OperationRecord.deposit(id, bankId, STONE, 3L, 2L, enormous, AT)));

        Bank stored = server.open().banks().findBank(bankId).orElseThrow();
        assertThat(stored.line(STONE).accumulatedValue().toPlainString())
                .isEqualTo("1234567890123456789012345678901234567890.0001");
        OperationRecord record = backend.banks().findOperation(id).orElseThrow();
        assertThat(record.valueDelta()).isEqualByComparingTo(enormous);
        assertThat(record.timestamp()).isEqualTo(AT);
    }

    @ParameterizedTest
    @EnumSource(MongoBackend.WriteMode.class)
    void plusieurs_clients_et_threads_n_appliquent_chaque_operation_qu_une_fois(MongoBackend.WriteMode mode)
            throws Exception {
        List<MongoBackend> clients = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            clients.add(server.open(mode, Duration.ofSeconds(60)));
        }

        LedgerConcurrencyScenario.run(clients, 16, 6);
        // Deux scenarios simultanes sur deux banques, 2 clients x 3 threads chacun.
        ConcurrentRunner.run(2, scenario -> LedgerConcurrencyScenario.run(clients.subList(2 * scenario,
                2 * scenario + 2), 6, 6));

        assertThat(MongoJournalInvariants.violations(server.database())).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(MongoBackend.WriteMode.class)
    void un_meme_identifiant_soumis_a_deux_banques_en_meme_temps_n_est_applique_qu_une_fois(
            MongoBackend.WriteMode mode) throws Exception {
        List<MongoBackend> clients = List.of(server.open(mode, Duration.ofSeconds(60)),
                server.open(mode, Duration.ofSeconds(60)));
        List<BankId> banks = List.of(TestBankIds.random(), TestBankIds.random());
        for (BankId bank : banks) {
            clients.get(0).banks().createBank(bank, OWNER, ValuationMode.ACCUMULATED);
        }
        for (int round = 0; round < 30; round++) {
            OperationId id = OperationId.random();
            List<ApplyOutcome> outcomes = Collections.synchronizedList(new ArrayList<>());
            ConcurrentRunner.run(2, thread -> outcomes.add(clients.get(thread).banks().apply(id, banks.get(thread),
                    bank -> MutationDecision.accept(bank.withLine(STONE, oneMore(bank.line(STONE))),
                            OperationRecord.deposit(id, bank.id(), STONE, 1L, 1L, BigDecimal.ONE, AT)))));
            assertThat(outcomes).filteredOn(outcome -> outcome instanceof ApplyOutcome.Applied).hasSize(1);
            assertThat(outcomes).filteredOn(outcome -> outcome instanceof ApplyOutcome.Duplicate).hasSize(1);
        }
        long total = 0;
        for (BankId bank : banks) {
            total += clients.get(0).banks().findBank(bank).orElseThrow().line(STONE).quantity();
        }
        assertThat(total).isEqualTo(30L);
        List<String> violations = MongoJournalInvariants.violations(server.database());
        if (mode == MongoBackend.WriteMode.AUTO && MongoTestServer.replicaSet()) {
            assertThat(violations).as("transactions : aucun perdant n'a ecrit").isEmpty();
        } else {
            assertThat(violations).as("journal en attente : un perdant annule garde deux versions de plus ; stock,"
                    + " valeur et attentes exacts").allMatch(violation -> violation.startsWith("version"));
        }
    }

    @Test
    void un_arret_entre_validation_et_journal_est_rattrape_au_demarrage_sans_double_effet() {
        MongoBackend first = server.open(MongoBackend.WriteMode.WITHOUT_TRANSACTIONS, Duration.ofSeconds(10));
        BankId bankId = TestBankIds.random();
        Bank opened = first.banks().createBank(bankId, OWNER, ValuationMode.ACCUMULATED);
        OperationId id = OperationId.random();
        OperationRecord record = OperationRecord.deposit(id, bankId, STONE, 5L, 5L, new BigDecimal("2.5"), AT);
        Bank saved = opened.withLine(STONE, new StockLine(5L, 0L, new BigDecimal("2.5"))).withVersion(1L);
        simulateStopAfterValidation(PendingOperations.Kind.INSERT, record, opened, saved);

        MongoBackend restarted = server.open(MongoBackend.WriteMode.WITHOUT_TRANSACTIONS, Duration.ofSeconds(10));

        assertThat(rawBank(bankId).containsKey("pendingOp")).isFalse();
        assertThat(restarted.banks().findOperation(id)).contains(record);
        assertThat(restarted.banks().apply(id, bankId, bank -> {
            throw new AssertionError("rejeu : la mutation ne doit pas etre appelee");
        })).isInstanceOf(ApplyOutcome.Duplicate.class);
        assertThat(restarted.banks().findBank(bankId).orElseThrow().line(STONE).quantity()).isEqualTo(5L);
        assertThat(MongoJournalInvariants.violations(server.database())).isEmpty();
    }

    @Test
    void chaque_lecture_resout_l_operation_en_attente_qui_la_concerne() {
        MongoBackend backend = server.open(MongoBackend.WriteMode.WITHOUT_TRANSACTIONS, Duration.ofSeconds(10));
        BankId bankId = TestBankIds.random();
        Bank opened = backend.banks().createBank(bankId, OWNER, ValuationMode.ACCUMULATED);
        OperationId deposit = OperationId.random();
        OperationRecord depositRecord = OperationRecord.deposit(deposit, bankId, STONE, 4L, 4L, BigDecimal.ONE, AT);
        Bank afterDeposit = opened.withLine(STONE, new StockLine(4L, 0L, BigDecimal.ONE)).withVersion(1L);

        simulateStopAfterValidation(PendingOperations.Kind.INSERT, depositRecord, opened, afterDeposit);
        assertThat(backend.banks().findOperation(deposit)).as("findOperation").contains(depositRecord);

        OperationId withdrawal = OperationId.random();
        OperationRecord reserved = OperationRecord.reservation(withdrawal, bankId, STONE, 3L, 3L,
                BigDecimal.ONE.negate(), AT, UUID.randomUUID(), 0L);
        Bank afterReservation = afterDeposit.withLine(STONE, new StockLine(1L, 0L, BigDecimal.ZERO)).withVersion(2L);
        simulateStopAfterValidation(PendingOperations.Kind.INSERT, reserved, afterDeposit, afterReservation);
        assertThat(backend.banks().reservedWithdrawals()).as("reservedWithdrawals").containsExactly(reserved);

        OperationRecord settled = reserved.settle(OperationState.COMMITTED, 3L, 0L,
                BigDecimal.ZERO);
        Bank afterSettle = afterReservation.withVersion(3L);
        simulateStopAfterValidation(PendingOperations.Kind.REPLACE, settled, afterReservation, afterSettle);
        assertThat(backend.banks().history(bankId, 0, 10)).as("history").contains(settled);
        assertThat(backend.banks().findBank(bankId)).as("findBank").contains(afterSettle);
        assertThat(MongoJournalInvariants.violations(server.database())).isEmpty();
    }

    @Test
    void une_attente_dont_l_identifiant_appartient_a_une_autre_banque_est_annulee() {
        MongoBackend backend = server.open(MongoBackend.WriteMode.WITHOUT_TRANSACTIONS, Duration.ofSeconds(10));
        BankId winner = TestBankIds.random();
        BankId loser = TestBankIds.random();
        backend.banks().createBank(winner, OWNER, ValuationMode.ACCUMULATED);
        Bank opened = backend.banks().createBank(loser, OWNER, ValuationMode.ACCUMULATED);
        OperationId id = OperationId.random();
        backend.banks().apply(id, winner, bank -> MutationDecision.accept(bank.withLine(STONE, oneMore(bank.line(STONE))),
                OperationRecord.deposit(id, winner, STONE, 1L, 1L, BigDecimal.ONE, AT)));
        Bank lost = opened.withLine(STONE, StockLine.EMPTY.plus(7L)).withVersion(1L);
        simulateStopAfterValidation(PendingOperations.Kind.INSERT,
                OperationRecord.deposit(id, loser, STONE, 7L, 7L, BigDecimal.TEN, AT), opened, lost);

        Bank restored = backend.banks().findBank(loser).orElseThrow();

        assertThat(restored.lines()).isEmpty();
        assertThat(restored.version()).isEqualTo(2L);
        assertThat(backend.banks().findOperation(id).orElseThrow().bankId()).isEqualTo(winner);
    }

    @Test
    void un_resolveur_en_retard_n_ecrase_jamais_une_evolution_plus_recente_du_journal() {
        MongoBackend backend = server.open(MongoBackend.WriteMode.WITHOUT_TRANSACTIONS, Duration.ofSeconds(10));
        BankId bankId = TestBankIds.random();
        Bank opened = backend.banks().createBank(bankId, OWNER, ValuationMode.ACCUMULATED);
        OperationId id = OperationId.random();
        OperationRecord reserved = OperationRecord.reservation(id, bankId, STONE, 3L, 3L, BigDecimal.ONE.negate(), AT,
                UUID.randomUUID(), 0L);
        backend.banks().apply(id, bankId, bank -> MutationDecision.accept(bank, reserved));
        backend.banks().settle(id, (bank, record) -> MutationDecision.accept(bank,
                record.settle(OperationState.COMMITTED, 3L, 0L, BigDecimal.ZERO)));

        // Resolveur reste sur une attente anterieure (version 1, entree RESERVED) de la meme operation.
        try (MongoClient client = MongoTestServer.rawClient()) {
            PendingOperations pending = new PendingOperations(MongoCollections.of(client.getDatabase(server.database())),
                    new BankDocuments(Clock.systemUTC()), Clock.systemUTC(), MongoTestServer.LOGGER);
            Document stale = pending.entry(PendingOperations.Kind.REPLACE, reserved, opened, opened.withVersion(1L));
            assertThat(pending.resolve(bankId.toString(), stale)).isEqualTo(PendingOperations.Resolution.PROPAGATED);
        }

        assertThat(backend.banks().findOperation(id).orElseThrow().state()).isEqualTo(OperationState.COMMITTED);
        assertThat(backend.banks().findBank(bankId).orElseThrow().version()).isEqualTo(2L);
        assertThat(rawBank(bankId).containsKey("pendingOp")).isFalse();
    }

    @Test
    void un_schema_plus_recent_est_refuse() {
        server.open();
        try (MongoClient client = MongoTestServer.rawClient()) {
            client.getDatabase(server.database()).getCollection("schema")
                    .updateOne(Filters.eq("_id", "schema"), new Document("$set", new Document("version", 99)));
        }
        assertThatThrownBy(() -> MongoBackend.open(server.settings(), Clock.systemUTC(), Logger.getAnonymousLogger()))
                .isInstanceOf(StorageException.class).hasMessageContaining("v99");
    }

    /**
     * Reproduit un arret du serveur Minecraft juste apres l'etape 1 du protocole : etat et attente
     * ecrits dans le document banque, rien dans le journal.
     */
    private void simulateStopAfterValidation(PendingOperations.Kind kind, OperationRecord record, Bank previous,
                                             Bank saved) {
        try (MongoClient client = MongoTestServer.rawClient()) {
            MongoCollections collections = MongoCollections.of(client.getDatabase(server.database()));
            BankDocuments documents = new BankDocuments(Clock.systemUTC());
            PendingOperations pending = new PendingOperations(collections, documents, Clock.systemUTC(),
                    MongoTestServer.LOGGER);
            Document entry = pending.entry(kind, record, previous, saved);
            assertThat(collections.banks().updateOne(Filters.and(Filters.eq("_id", previous.id().toString()),
                    Filters.eq("version", previous.version())), pending.validation(entry, saved)).getMatchedCount())
                    .isEqualTo(1L);
        }
    }

    private static StockLine oneMore(StockLine line) {
        return line.plus(1L).withAccumulatedValue(line.accumulatedValue().add(BigDecimal.ONE));
    }

    private Document rawBank(BankId bankId) {
        try (MongoClient client = MongoTestServer.rawClient()) {
            MongoCollection<Document> banks = client.getDatabase(server.database()).getCollection("banks");
            return banks.find(Filters.eq("_id", bankId.toString())).first();
        }
    }
}
