package dev.novadev.novabank.storage.sql;

import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.api.OperationId;
import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.api.operation.OperationStatus;
import dev.novadev.novabank.api.valuation.ValuationMode;
import dev.novadev.novabank.core.repository.ApplyOutcome;
import dev.novadev.novabank.core.testing.TestBankIds;
import dev.novadev.novabank.storage.sql.dialect.MariaDbDialect;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Conflits de verrous reels sur MySQL / MariaDB : attente de verrou depassee (1205) rejouee,
 * conflit persistant refuse sans rien ecrire, et code d'un vrai interblocage (1213) reconnu
 * comme rejouable.
 */
@RequiresSqlServer
class ServerLockConflictTest {

    private static final ItemKey STONE = TestMutations.STONE;
    /** Attente de verrou de ligne de 1 s : un verrou tenu plus longtemps fait echouer la tentative. */
    private static final MariaDbDialect IMPATIENT = new MariaDbDialect(Duration.ofSeconds(1),
            MariaDbDialect.DEFAULT_MIGRATION_LOCK_TIMEOUT);

    private ServerDatabase database;
    private SqlStorage storage;
    private BankId bankId;

    @BeforeEach
    void open() throws SQLException {
        database = ServerDatabase.create();
        storage = database.open(database.pool(IMPATIENT, ServerDatabase.POOL), IMPATIENT,
                SqlStorage.DEFAULT_TABLE_PREFIX);
        bankId = TestBankIds.random();
        storage.banks().createBank(bankId, EntityRef.player(UUID.randomUUID()), ValuationMode.DERIVED);
    }

    @AfterEach
    void close() throws SQLException {
        storage.close();
        database.close();
    }

    @Test
    void une_attente_de_verrou_depassee_est_rejouee_puis_appliquee_une_seule_fois() throws Exception {
        OperationId id = OperationId.random();
        CompletableFuture<ApplyOutcome> outcome;
        long started;
        try (Connection other = database.rawConnection()) {
            lockBank(other);
            started = System.nanoTime();
            outcome = CompletableFuture.supplyAsync(() -> storage.banks().apply(id, bankId, TestMutations.depositOf(id, 7L)));
            // Tenu 2,5 s : au moins deux tentatives echouent (attente de 1 s), la suivante passe.
            TimeUnit.MILLISECONDS.sleep(2_500);
            other.rollback();
        }

        assertThat(outcome.get(30, TimeUnit.SECONDS)).isInstanceOf(ApplyOutcome.Applied.class);
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isGreaterThan(Duration.ofSeconds(2));
        assertThat(storage.banks().findBank(bankId).orElseThrow().line(STONE).quantity()).isEqualTo(7L);
        assertThat(storage.banks().findBank(bankId).orElseThrow().version()).isEqualTo(1L);
    }

    @Test
    void un_verrou_tenu_plus_longtemps_que_toutes_les_tentatives_refuse_sans_rien_ecrire() throws Exception {
        OperationId id = OperationId.random();
        try (Connection other = database.rawConnection()) {
            lockBank(other);

            ApplyOutcome outcome = storage.banks().apply(id, bankId, TestMutations.depositOf(id, 7L));

            assertThat(outcome).isEqualTo(new ApplyOutcome.Rejected(OperationStatus.CONCURRENT_MODIFICATION));
            other.rollback();
        }
        assertThat(storage.banks().findOperation(id)).isEmpty();
        assertThat(storage.banks().findBank(bankId).orElseThrow().version()).isZero();
    }

    @Test
    void un_vrai_interblocage_porte_le_code_que_le_dialecte_rejoue() throws Exception {
        BankId second = TestBankIds.random();
        storage.banks().createBank(second, EntityRef.player(UUID.randomUUID()), ValuationMode.DERIVED);
        CountDownLatch bothLocked = new CountDownLatch(2);
        try (Connection first = database.rawConnection(); Connection other = database.rawConnection()) {
            CompletableFuture<SQLException> a = CompletableFuture.supplyAsync(() ->
                    lockInOrder(first, bankId, second, bothLocked));
            CompletableFuture<SQLException> b = CompletableFuture.supplyAsync(() ->
                    lockInOrder(other, second, bankId, bothLocked));
            SQLException failureA = a.get(30, TimeUnit.SECONDS);
            SQLException failureB = b.get(30, TimeUnit.SECONDS);

            SQLException victim = failureA != null ? failureA : failureB;
            assertThat((Throwable) victim).as("une des deux transactions est la victime de l'interblocage").isNotNull();
            assertThat(victim.getErrorCode()).isEqualTo(1213);
            assertThat(IMPATIENT.isRetryable(victim)).isTrue();
            assertThat(failureA == null || failureB == null).as("une seule victime").isTrue();
        }
    }

    private SQLException lockInOrder(Connection connection, BankId firstBank, BankId secondBank,
                                     CountDownLatch bothLocked) {
        try (Statement session = connection.createStatement()) {
            // Attente de verrou longue : seul le detecteur d'interblocage doit trancher.
            session.execute("SET SESSION innodb_lock_wait_timeout = 20");
            connection.setAutoCommit(false);
            lockRow(connection, firstBank);
            bothLocked.countDown();
            bothLocked.await(10, TimeUnit.SECONDS);
            lockRow(connection, secondBank);
            connection.rollback();
            return null;
        } catch (SQLException failure) {
            rollbackQuietly(connection);
            return failure;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    private void lockBank(Connection connection) throws SQLException {
        connection.setAutoCommit(false);
        lockRow(connection, bankId);
    }

    private static void lockRow(Connection connection, BankId bank) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT version FROM novabank_banks WHERE bank_id = ? FOR UPDATE")) {
            select.setString(1, bank.toString());
            select.executeQuery().close();
        }
    }

    private static void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // Connexion de test : la fermeture annulera de toute facon.
        }
    }
}
