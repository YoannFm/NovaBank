package dev.novadev.novabank.storage.sql;

import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.api.OperationId;
import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.api.valuation.ValuationMode;
import dev.novadev.novabank.core.delivery.UndeliveredPolicy;
import dev.novadev.novabank.core.ledger.BankLedger;
import dev.novadev.novabank.core.testing.MutablePriceCatalog;
import dev.novadev.novabank.core.testing.OutageWorkload;
import dev.novadev.novabank.core.testing.TestBankIds;
import java.sql.Connection;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * Panne reelle du serveur de base : le processus MySQL / MariaDB est tue (SIGKILL, via
 * {@code scripts/db/db-server.sh}) pendant que quatre threads deposent et retirent, puis relance.
 *
 * <p>Attendu : pendant la panne, chaque operation echoue proprement en
 * {@link StorageException} (aucune autre exception, mot de passe absent des messages) ; apres
 * le redemarrage (recuperation InnoDB), le pool se reconnecte seul, chaque operation annoncee
 * reussie est dans le journal, et le journal decrit exactement l'etat des banques (aucune
 * operation a moitie appliquee).
 */
@Tag(ServerDatabase.TAG)
@EnabledIf("dev.novadev.novabank.storage.sql.ServerDatabase#controllable")
class ServerOutageTest {

    private static final ItemKey STONE = ItemKey.minecraft("stone");
    private static final int WORKERS = 4;

    @Test
    void un_serveur_tue_pendant_les_operations_ne_laisse_rien_a_moitie_et_le_pool_se_reconnecte()
            throws Exception {
        try (ServerDatabase database = ServerDatabase.create();
             SqlStorage storage = database.open(SqlStorage.DEFAULT_TABLE_PREFIX)) {
            BankLedger ledger = new BankLedger(storage.banks(), new MutablePriceCatalog().price(STONE, "0.25"),
                    storage.deliveries(), UndeliveredPolicy.RETURN_TO_BANK, Clock.systemUTC());
            BankId bankId = TestBankIds.random();
            ledger.openBank(bankId, EntityRef.player(UUID.randomUUID()), ValuationMode.ACCUMULATED);

            OutageWorkload workload = OutageWorkload.start(ledger, bankId, STONE, WORKERS);
            TimeUnit.MILLISECONDS.sleep(1_500);
            long beforeKill = workload.successes();
            ServerDatabase.control("kill");
            TimeUnit.MILLISECONDS.sleep(4_000);
            int failuresDuringOutage = workload.storageFailures();
            ServerDatabase.control("start");
            int afterRestart = workload.awaitRecovery(20, Duration.ofSeconds(30));
            workload.stop();

            assertThat(workload.unexpected()).isEmpty();
            assertThat(beforeKill).as("operations reussies avant la panne").isPositive();
            assertThat(failuresDuringOutage).as("echecs propres pendant la panne").isPositive();
            assertThat(afterRestart).as("reconnexion du pool apres redemarrage").isGreaterThanOrEqualTo(20);
            String password = ServerDatabase.password();
            if (!password.isEmpty()) {
                assertThat(workload.failureMessages()).noneMatch(message -> message.contains(password));
            }
            for (OperationId deposit : workload.confirmedDeposits()) {
                assertThat(storage.banks().findOperation(deposit)).as("depot annonce reussi " + deposit).isPresent();
            }
            assertThat(storage.banks().findBank(bankId).orElseThrow().line(STONE).quantity()).isNotNegative();
            try (Connection connection = database.rawConnection()) {
                assertThat(JournalInvariants.violations(connection, SqlStorage.DEFAULT_TABLE_PREFIX)).isEmpty();
            }
        }
    }
}
