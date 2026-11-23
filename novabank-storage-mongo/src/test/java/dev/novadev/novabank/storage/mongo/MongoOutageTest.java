package dev.novadev.novabank.storage.mongo;

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
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Panne reelle : le processus mongod est tue (SIGKILL) pendant que quatre threads deposent et
 * retirent, puis relance. Attendu : echecs propres en StorageException pendant la panne,
 * reconnexion seule du client, chaque depot annonce reussi present dans le journal, et apres
 * rattrapage aucune operation a moitie appliquee ni restee en attente.
 */
@RequiresMongoServer
@EnabledIf("dev.novadev.novabank.storage.mongo.MongoTestServer#controllable")
class MongoOutageTest {

    private static final ItemKey STONE = ItemKey.minecraft("stone");

    @ParameterizedTest
    @EnumSource(MongoBackend.WriteMode.class)
    void un_serveur_tue_pendant_les_operations_ne_laisse_rien_a_moitie(MongoBackend.WriteMode mode) throws Exception {
        try (MongoTestServer server = MongoTestServer.create()) {
            // Selection du serveur en 1 s : les rejeux des erreurs transitoires (3 x 1 s) s'epuisent
            // pendant la panne, qui doit donc produire des echecs propres.
            MongoBackend backend = server.open(mode, Duration.ofSeconds(10), server.settings(1_000));
            BankLedger ledger = new BankLedger(backend.banks(), new MutablePriceCatalog().price(STONE, "0.25"),
                    backend.deliveries(), UndeliveredPolicy.RETURN_TO_BANK, Clock.systemUTC());
            BankId bankId = TestBankIds.random();
            ledger.openBank(bankId, EntityRef.player(UUID.randomUUID()), ValuationMode.ACCUMULATED);

            OutageWorkload workload = OutageWorkload.start(ledger, bankId, STONE, 4);
            TimeUnit.MILLISECONDS.sleep(1_500);
            long beforeKill = workload.successes();
            MongoTestServer.control("kill");
            TimeUnit.MILLISECONDS.sleep(8_000);
            int failuresDuringOutage = workload.storageFailures();
            MongoTestServer.control("start");
            int afterRestart = workload.awaitRecovery(20, Duration.ofSeconds(45));
            workload.stop();

            assertThat(workload.unexpected()).isEmpty();
            assertThat(beforeKill).as("operations reussies avant la panne").isPositive();
            assertThat(failuresDuringOutage).as("echecs propres pendant la panne").isPositive();
            assertThat(afterRestart).as("reconnexion apres redemarrage").isGreaterThanOrEqualTo(20);
            for (OperationId deposit : workload.confirmedDeposits()) {
                assertThat(backend.banks().findOperation(deposit)).as("depot annonce reussi " + deposit).isPresent();
            }
            // Rattrapage complet (resout toute attente laissee par la panne), puis invariants bruts.
            backend.banks().reservedWithdrawals();
            assertThat(backend.banks().findBank(bankId).orElseThrow().line(STONE).quantity()).isNotNegative();
            assertThat(MongoJournalInvariants.violations(server.database())).isEmpty();
        }
    }
}
