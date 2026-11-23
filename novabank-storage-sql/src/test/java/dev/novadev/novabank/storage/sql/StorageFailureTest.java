package dev.novadev.novabank.storage.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.api.OperationId;
import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.api.operation.OperationStatus;
import dev.novadev.novabank.api.valuation.ValuationMode;
import dev.novadev.novabank.core.bank.Bank;
import dev.novadev.novabank.core.journal.OperationRecord;
import dev.novadev.novabank.core.journal.OperationState;
import dev.novadev.novabank.core.repository.ApplyOutcome;
import dev.novadev.novabank.core.repository.BankRepository;
import dev.novadev.novabank.core.repository.MutationDecision;
import dev.novadev.novabank.core.repository.StorageException;
import dev.novadev.novabank.core.testing.TestBankIds;
import dev.novadev.novabank.storage.sql.pool.ConnectionPools;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pannes de base : ouverture impossible, erreur en milieu de transaction, conflits.
 */
class StorageFailureTest {

    private static final ItemKey STONE = ItemKey.minecraft("stone");

    @TempDir
    Path directory;

    private Path file;
    private FaultInjectingDataSource faults;
    private SqlStorage storage;
    private BankRepository repository;
    private BankId bankId;

    @BeforeEach
    void open() {
        file = directory.resolve("faults.db");
        faults = new FaultInjectingDataSource(SqliteTestSupport.pool(file));
        storage = SqliteTestSupport.open(faults);
        repository = storage.banks();
        bankId = TestBankIds.random();
        repository.createBank(bankId, EntityRef.player(UUID.randomUUID()), ValuationMode.ACCUMULATED);
    }

    @AfterEach
    void close() {
        storage.close();
    }

    @Test
    void un_chemin_invalide_donne_une_erreur_de_stockage_claire() throws IOException {
        Path notADirectory = Files.createFile(directory.resolve("plain-file"));
        Path impossible = notADirectory.resolve("bank.db");

        assertThatThrownBy(() -> SqliteTestSupport.pool(impossible))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("Impossible d'ouvrir la base SQLite")
                .hasMessageContaining(impossible.toString())
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void un_dossier_en_lecture_seule_donne_une_erreur_de_stockage_claire() throws IOException {
        Path readOnly = Files.createDirectory(directory.resolve("read-only"));
        assumeTrue(readOnly.toFile().setWritable(false, false), "permissions POSIX indisponibles");
        try {
            assumeTrue(!Files.isWritable(readOnly), "utilisateur privilegie : le dossier reste inscriptible");

            assertThatThrownBy(() -> SqliteTestSupport.open(readOnly.resolve("bank.db")))
                    .isInstanceOf(StorageException.class)
                    .hasMessageContaining(readOnly.toString());
        } finally {
            readOnly.toFile().setWritable(true, false);
        }
    }

    @Test
    void une_taille_de_pool_invalide_est_refusee() {
        assertThatThrownBy(() -> ConnectionPools.sqlite(file, SqliteTestSupport.driver(), SqliteTestSupport.DIALECT, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void une_panne_en_milieu_de_transaction_annule_tout() throws SQLException {
        OperationId id = OperationId.random();
        faults.inject(sql -> sql.startsWith("INSERT INTO novabank_stock_lines"), 1,
                FaultInjectingDataSource.failing("disque plein"));

        assertThatThrownBy(() -> repository.apply(id, bankId, TestMutations.depositOf(id, 5L)))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("operation " + id)
                .hasMessageContaining("disque plein")
                .hasCauseInstanceOf(SQLException.class);

        assertNothingWritten(id);
        assertThat(repository.apply(id, bankId, TestMutations.depositOf(id, 5L))).isInstanceOf(ApplyOutcome.Applied.class);
        assertThat(repository.findBank(bankId).orElseThrow().line(STONE).quantity()).isEqualTo(5L);
    }

    @Test
    void une_panne_au_commit_annule_tout() throws SQLException {
        OperationId id = OperationId.random();
        faults.inject("COMMIT"::equals, 1, FaultInjectingDataSource.failing("coupure"));

        assertThatThrownBy(() -> repository.apply(id, bankId, TestMutations.depositOf(id, 2L))).isInstanceOf(StorageException.class);

        assertNothingWritten(id);
    }

    @Test
    void une_panne_de_settle_laisse_le_retrait_reserve() {
        OperationId deposit = OperationId.random();
        repository.apply(deposit, bankId, TestMutations.depositOf(deposit, 4L));
        OperationId withdrawal = OperationId.random();
        repository.apply(withdrawal, bankId, bank -> MutationDecision.accept(
                bank.withLine(STONE, bank.line(STONE).minus(4L)),
                OperationRecord.reservation(withdrawal, bankId, STONE, 4L, 4L, new BigDecimal("-4"), Instant.EPOCH,
                        UUID.randomUUID(), 0L)));
        faults.inject(sql -> sql.startsWith("UPDATE novabank_operations"), 1,
                FaultInjectingDataSource.failing("panne"));

        assertThatThrownBy(() -> repository.settle(withdrawal, (bank, record) -> MutationDecision.accept(
                bank.withLine(STONE, bank.line(STONE).plus(4L)),
                record.settle(OperationState.RETURNED, 0L, 4L, BigDecimal.ONE))))
                .isInstanceOf(StorageException.class);

        Bank bank = repository.findBank(bankId).orElseThrow();
        assertThat(bank.version()).isEqualTo(2L);
        assertThat(bank.line(STONE).quantity()).isZero();
        assertThat(repository.reservedWithdrawals()).extracting(OperationRecord::operationId).containsExactly(withdrawal);
    }

    @Test
    void un_conflit_de_version_est_rejoue_puis_applique_une_seule_fois() {
        OperationId id = OperationId.random();
        faults.inject(sql -> sql.startsWith("UPDATE novabank_banks"), 1, FaultInjectingDataSource.noRowAffected());

        ApplyOutcome outcome = repository.apply(id, bankId, TestMutations.depositOf(id, 3L));

        assertThat(outcome).isInstanceOf(ApplyOutcome.Applied.class);
        assertThat(faults.hits()).isEqualTo(2);
        Bank bank = repository.findBank(bankId).orElseThrow();
        assertThat(bank.version()).isEqualTo(1L);
        assertThat(bank.line(STONE).quantity()).isEqualTo(3L);
    }

    @Test
    void des_conflits_persistants_refusent_l_operation_sans_rien_ecrire() throws SQLException {
        OperationId id = OperationId.random();
        faults.inject(sql -> sql.startsWith("UPDATE novabank_banks"), Integer.MAX_VALUE,
                FaultInjectingDataSource.noRowAffected());

        ApplyOutcome outcome = repository.apply(id, bankId, TestMutations.depositOf(id, 3L));

        assertThat(outcome).isEqualTo(new ApplyOutcome.Rejected(OperationStatus.CONCURRENT_MODIFICATION));
        assertThat(faults.hits()).isEqualTo(SqlTransactions.MAX_ATTEMPTS);
        assertNothingWritten(id);
    }

    @Test
    void une_violation_d_unicite_du_journal_est_traitee_comme_un_conflit() throws SQLException {
        SQLException uniqueViolation = realUniqueViolation();
        OperationId id = OperationId.random();
        faults.inject(sql -> sql.startsWith("INSERT INTO novabank_operations"), 1,
                FaultInjectingDataSource.rethrowing(uniqueViolation));

        ApplyOutcome outcome = repository.apply(id, bankId, TestMutations.depositOf(id, 1L));

        assertThat(outcome).isInstanceOf(ApplyOutcome.Applied.class);
        assertThat(repository.findBank(bankId).orElseThrow().version()).isEqualTo(1L);
    }

    @Test
    void une_autre_erreur_d_insertion_du_journal_n_est_pas_rejouee() {
        OperationId id = OperationId.random();
        faults.inject(sql -> sql.startsWith("INSERT INTO novabank_operations"), 1,
                FaultInjectingDataSource.failing("contrainte"));

        assertThatThrownBy(() -> repository.apply(id, bankId, TestMutations.depositOf(id, 1L))).isInstanceOf(StorageException.class);
        assertThat(faults.hits()).isEqualTo(1);
    }

    @Test
    void les_lectures_et_l_ajout_de_livraison_signalent_les_pannes() {
        faults.inject(sql -> sql.startsWith("SELECT"), Integer.MAX_VALUE, FaultInjectingDataSource.failing("lecture"));

        assertThatThrownBy(() -> repository.findBank(bankId)).isInstanceOf(StorageException.class)
                .hasMessageContaining("lecture de la banque");
        assertThatThrownBy(repository::reservedWithdrawals).isInstanceOf(StorageException.class);
        assertThatThrownBy(() -> storage.deliveries().pendingFor(UUID.randomUUID()))
                .isInstanceOf(StorageException.class);
    }

    @Test
    void apres_fermeture_toute_operation_echoue_proprement() {
        storage.close();

        assertThatThrownBy(() -> repository.findBank(bankId)).isInstanceOf(StorageException.class);
        assertThatThrownBy(() -> repository.createBank(TestBankIds.random(), EntityRef.player(UUID.randomUUID()),
                ValuationMode.DERIVED)).isInstanceOf(StorageException.class);
    }

    private void assertNothingWritten(OperationId id) throws SQLException {
        faults.clear();
        assertThat(repository.findOperation(id)).isEmpty();
        Bank bank = repository.findBank(bankId).orElseThrow();
        assertThat(bank.version()).isZero();
        assertThat(bank.lines()).isEmpty();
        try (Connection connection = SqliteTestSupport.rawConnection(file)) {
            assertThat(JournalInvariants.single(connection, "SELECT COUNT(*) FROM novabank_stock_lines")).isEqualTo("0");
            assertThat(JournalInvariants.single(connection, "SELECT COUNT(*) FROM novabank_operations")).isEqualTo("0");
        }
    }

    private SQLException realUniqueViolation() throws SQLException {
        try (Connection connection = SqliteTestSupport.rawConnection(directory.resolve("unique.db"));
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE u (id TEXT PRIMARY KEY)");
            statement.executeUpdate("INSERT INTO u VALUES ('x')");
            try {
                statement.executeUpdate("INSERT INTO u VALUES ('x')");
            } catch (SQLException failure) {
                assertThat(SqliteTestSupport.DIALECT.isUniqueViolation(failure)).isTrue();
                return failure;
            }
        }
        throw new AssertionError("Violation d'unicite attendue");
    }

}
