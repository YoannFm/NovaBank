package dev.novadev.novabank.storage.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.api.OperationId;
import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.api.operation.OperationStatus;
import dev.novadev.novabank.api.valuation.ValuationMode;
import dev.novadev.novabank.core.delivery.PendingDelivery;
import dev.novadev.novabank.core.repository.ApplyOutcome;
import dev.novadev.novabank.core.repository.StorageException;
import dev.novadev.novabank.core.testing.TestBankIds;
import dev.novadev.novabank.storage.sql.dialect.SqlDialect;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Erreurs que le dialecte declare rejouables (interblocage, attente de verrou depassee sous
 * MySQL / MariaDB), injectees sur une base SQLite reelle : la transaction est annulee puis
 * rejouee ; si l'erreur persiste, rien n'est ecrit (refus pour une operation de banque,
 * {@link StorageException} pour les autres ecritures). Le cas reel est couvert par
 * {@code ServerLockConflictTest}.
 */
class RetryableErrorTest {

    private static final int DEADLOCK = 1213;

    @TempDir
    Path directory;

    private FaultInjectingDataSource faults;
    private SqlStorage storage;
    private BankId bankId;

    @BeforeEach
    void open() {
        faults = new FaultInjectingDataSource(SqliteTestSupport.pool(directory.resolve("retry.db")));
        storage = SqlStorage.open(faults, deadlockAwareSqlite(), SqlStorage.DEFAULT_TABLE_PREFIX, Clock.systemUTC());
        bankId = TestBankIds.random();
        storage.banks().createBank(bankId, EntityRef.player(UUID.randomUUID()), ValuationMode.ACCUMULATED);
    }

    @AfterEach
    void close() {
        storage.close();
    }

    @Test
    void un_interblocage_est_rejoue_et_l_operation_appliquee_une_seule_fois() {
        OperationId id = OperationId.random();
        faults.inject(sql -> sql.startsWith("UPDATE novabank_banks"), 2, FaultInjectingDataSource.rethrowing(deadlock()));

        ApplyOutcome outcome = storage.banks().apply(id, bankId, TestMutations.depositOf(id, 3L));

        assertThat(outcome).isInstanceOf(ApplyOutcome.Applied.class);
        assertThat(faults.hits()).isEqualTo(3);
        assertThat(storage.banks().findBank(bankId).orElseThrow().version()).isEqualTo(1L);
        assertThat(storage.banks().findBank(bankId).orElseThrow().line(TestMutations.STONE).quantity()).isEqualTo(3L);
    }

    @Test
    void un_interblocage_persistant_refuse_l_operation_sans_rien_ecrire() {
        OperationId id = OperationId.random();
        faults.inject(sql -> sql.startsWith("UPDATE novabank_banks"), Integer.MAX_VALUE,
                FaultInjectingDataSource.rethrowing(deadlock()));

        ApplyOutcome outcome = storage.banks().apply(id, bankId, TestMutations.depositOf(id, 3L));

        assertThat(outcome).isEqualTo(new ApplyOutcome.Rejected(OperationStatus.CONCURRENT_MODIFICATION));
        assertThat(faults.hits()).isEqualTo(SqlTransactions.MAX_ATTEMPTS);
        faults.clear();
        assertThat(storage.banks().findOperation(id)).isEmpty();
        assertThat(storage.banks().findBank(bankId).orElseThrow().version()).isZero();
    }

    @Test
    void les_autres_ecritures_rejouent_aussi_puis_signalent_un_conflit_persistant() {
        UUID player = UUID.randomUUID();
        PendingDelivery delivery = new PendingDelivery(OperationId.random(), player, ItemKey.minecraft("dirt"), 4L);
        faults.inject(sql -> sql.startsWith("INSERT INTO novabank_pending_deliveries"), 1,
                FaultInjectingDataSource.rethrowing(deadlock()));

        storage.deliveries().add(delivery);
        assertThat(storage.deliveries().pendingFor(player)).containsExactly(delivery);

        faults.inject(sql -> sql.startsWith("INSERT INTO novabank_active_banks"), Integer.MAX_VALUE,
                FaultInjectingDataSource.rethrowing(deadlock()));
        assertThatThrownBy(() -> storage.activeBanks().save(player, EntityRef.player(player)))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("Conflit d'ecriture persistant")
                .hasMessageContaining("rien n'a ete ecrit");
        faults.clear();
        assertThat(storage.activeBanks().find(player)).isEmpty();
    }

    private static SQLException deadlock() {
        return new SQLException("Deadlock found when trying to get lock", "40001", DEADLOCK);
    }

    /** SQLite dont seules les erreurs rejouables suivent la regle MySQL (code 1213). */
    private static SqlDialect deadlockAwareSqlite() {
        return (SqlDialect) Proxy.newProxyInstance(RetryableErrorTest.class.getClassLoader(),
                new Class<?>[] {SqlDialect.class}, (proxy, method, args) -> method.getName().equals("isRetryable")
                        ? ((SQLException) args[0]).getErrorCode() == DEADLOCK
                        : method.invoke(SqliteTestSupport.DIALECT, args));
    }
}
