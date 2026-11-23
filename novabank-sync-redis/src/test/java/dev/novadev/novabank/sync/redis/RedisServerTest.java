package dev.novadev.novabank.sync.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.core.sync.BankChange;
import dev.novadev.novabank.core.sync.SyncEvent;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Bus sur un vrai serveur Redis, lance par {@code scripts/db/with-db.sh redis mvn -B -o test
 * -pl novabank-sync-redis -Dgroups=redis-server}. Ignore sans serveur.
 */
@Tag("redis-server")
@EnabledIfEnvironmentVariable(named = "NOVABANK_IT_DB_PRODUCT", matches = "redis")
class RedisServerTest {

    private static final BankId BANK = new BankId(UUID.fromString("00000000-0000-0000-0000-0000000000cc"));

    private static RedisConnectionSettings settings(String prefix) {
        return new RedisConnectionSettings(System.getenv("NOVABANK_IT_DB_HOST"),
                Integer.parseInt(System.getenv("NOVABANK_IT_DB_PORT")), "", false, prefix, 1_000, 1_000);
    }

    private static Logger quietLogger() {
        Logger logger = Logger.getLogger("redis-it-" + UUID.randomUUID());
        logger.setLevel(Level.OFF);
        return logger;
    }

    private static void awaitTrue(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("condition jamais atteinte");
            }
            Thread.sleep(25);
        }
    }

    private static void server(String action) throws IOException, InterruptedException {
        Process process = new ProcessBuilder("bash", System.getenv("NOVABANK_IT_DB_CTL"), "redis", action)
                .inheritIO().start();
        assertThat(process.waitFor()).isZero();
    }

    private static SyncEvent event(String origin, long version) {
        return new SyncEvent(origin, new BankChange(BANK, version, 0, Instant.ofEpochMilli(1_000 + version)));
    }

    @Test
    void aMessagePublishedByOneServerReachesTheOthersAndEveryoneSeesItsOwnEchoToFilter() throws Exception {
        String prefix = "t" + UUID.randomUUID().toString().substring(0, 8);
        try (RedisSyncBus one = new RedisSyncBus(settings(prefix), quietLogger());
             RedisSyncBus two = new RedisSyncBus(settings(prefix), quietLogger());
             RedisSyncBus other = new RedisSyncBus(settings(prefix + "-other"), quietLogger())) {
            List<SyncEvent> atTwo = new CopyOnWriteArrayList<>();
            List<SyncEvent> atOne = new CopyOnWriteArrayList<>();
            List<SyncEvent> atOtherNetwork = new CopyOnWriteArrayList<>();
            one.subscribe(atOne::add);
            two.subscribe(atTwo::add);
            other.subscribe(atOtherNetwork::add);
            awaitTrue(() -> one.status().connected() && two.status().connected() && other.status().connected());

            assertThat(one.publish(event("srv-1", 1))).isTrue();

            awaitTrue(() -> atTwo.size() == 1 && atOne.size() == 1);
            assertThat(atTwo).containsExactly(event("srv-1", 1));
            Thread.sleep(300);
            assertThat(atOtherNetwork).as("un autre prefixe est un autre reseau").isEmpty();
        }
    }

    @Test
    void survivesAHardKillOfRedisAndResumesAfterRestart() throws Exception {
        assumeTrue(System.getenv("NOVABANK_IT_DB_CTL") != null, "pas de script de pilotage du serveur (NOVABANK_IT_DB_CTL)");
        String prefix = "k" + UUID.randomUUID().toString().substring(0, 8);
        try (RedisSyncBus publisher = new RedisSyncBus(settings(prefix), quietLogger());
             RedisSyncBus subscriber = new RedisSyncBus(settings(prefix), quietLogger())) {
            List<SyncEvent> received = new CopyOnWriteArrayList<>();
            subscriber.subscribe(received::add);
            awaitTrue(() -> subscriber.status().connected() && publisher.status().connected());
            publisher.publish(event("srv-1", 1));
            awaitTrue(() -> received.size() == 1);

            server("kill");
            awaitTrue(() -> !subscriber.status().connected());
            // Redis est mort : aucune exception, jamais bloque, et la publication finit par etre refusee.
            long start = System.nanoTime();
            awaitTrue(() -> !publisher.publish(event("srv-1", 2)) || !publisher.status().connected());
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(20));
            assertThat(subscriber.status().lastError()).isNotEmpty();

            server("start");
            awaitTrue(() -> subscriber.status().connected());
            // Le publieur retablit sa liaison au premier envoi suivant.
            awaitTrue(() -> {
                publisher.publish(event("srv-1", 3));
                return received.stream().anyMatch(e -> e.change().version() == 3);
            });
            assertThat(subscriber.status().connected()).isTrue();
        }
    }
}
