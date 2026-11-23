package dev.novadev.novabank.sync.redis;

import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.core.sync.BankChange;
import dev.novadev.novabank.core.sync.SyncEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

/**
 * Comportement du bus face a un serveur RESP simule : message distant, message invalide, perte de
 * liaison puis reconnexion, controle de vie par PING.
 */
class RedisSyncBusFakeServerTest {

    private static final BankId BANK = new BankId(UUID.fromString("00000000-0000-0000-0000-0000000000bb"));

    private static Logger quietLogger() {
        Logger logger = Logger.getLogger("redis-bus-test-" + UUID.randomUUID());
        logger.setLevel(Level.OFF);
        return logger;
    }

    private static RedisConnectionSettings settings(int port) {
        return new RedisConnectionSettings("127.0.0.1", port, "", false, "nb", 500, 500);
    }

    private static byte[] reply(List<String> command) {
        return switch (command.get(0)) {
            case "SUBSCRIBE" -> FakeRedisServer.array(FakeRedisServer.bulk("subscribe"),
                    FakeRedisServer.bulk(command.get(1)), FakeRedisServer.integer(1));
            case "PING" -> FakeRedisServer.array(FakeRedisServer.bulk("pong"), FakeRedisServer.bulk(""));
            case "PUBLISH" -> FakeRedisServer.integer(1);
            default -> FakeRedisServer.error("ERR inconnu");
        };
    }

    private static void awaitTrue(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("condition jamais atteinte");
            }
            Thread.sleep(20);
        }
    }

    private static byte[] channelMessage(String payload) {
        return FakeRedisServer.array(FakeRedisServer.bulk("message"), FakeRedisServer.bulk("nb:bank-changes"),
                FakeRedisServer.bulk(payload));
    }

    @Test
    void deliversValidMessagesIgnoresInvalidOnesAndSurvivesAFailingReceiver() throws Exception {
        try (FakeRedisServer server = new FakeRedisServer(RedisSyncBusFakeServerTest::reply);
             RedisSyncBus bus = new RedisSyncBus(settings(server.port()), quietLogger())) {
            List<SyncEvent> received = new CopyOnWriteArrayList<>();
            bus.subscribe(event -> {
                if (event.change().version() == 1) {
                    throw new IllegalStateException("boom");
                }
                received.add(event);
            });
            awaitTrue(() -> bus.status().connected());
            SyncEvent first = new SyncEvent("srv-2", new BankChange(BANK, 1, 0, Instant.ofEpochMilli(1_000)));
            SyncEvent second = new SyncEvent("srv-2", new BankChange(BANK, 2, 0, Instant.ofEpochMilli(2_000)));

            server.push(channelMessage(SyncPayloads.encode(first)));
            server.push(channelMessage("not a valid payload"));
            server.push(channelMessage(SyncPayloads.encode(second)));

            awaitTrue(() -> received.size() == 1);
            assertThat(received).containsExactly(second);
        }
    }

    @Test
    void publishesThroughPublishAndReportsConnected() throws Exception {
        try (FakeRedisServer server = new FakeRedisServer(RedisSyncBusFakeServerTest::reply);
             RedisSyncBus bus = new RedisSyncBus(settings(server.port()), quietLogger())) {
            awaitTrue(() -> bus.status().connected());
            SyncEvent event = new SyncEvent("srv-1", new BankChange(BANK, 5, 1, Instant.ofEpochMilli(5_000)));

            assertThat(bus.publish(event)).isTrue();

            awaitTrue(() -> server.commands.stream().anyMatch(c -> c.get(0).equals("PUBLISH")));
            assertThat(server.commands).filteredOn(command -> command.get(0).equals("PUBLISH")).singleElement()
                    .satisfies(command -> {
                        assertThat(command.get(1)).isEqualTo("nb:bank-changes");
                        assertThat(SyncPayloads.decode(command.get(2))).contains(event);
                    });
            assertThat(bus.status().description()).isEqualTo("127.0.0.1:" + server.port());
        }
    }

    @Test
    void aFailingPublishIsReportedButDoesNotHideTheSubscriptionLink() throws Exception {
        try (FakeRedisServer server = new FakeRedisServer(command -> command.get(0).equals("PUBLISH")
                ? FakeRedisServer.error("ERR no permission") : reply(command));
             RedisSyncBus bus = new RedisSyncBus(settings(server.port()), quietLogger())) {
            awaitTrue(() -> bus.status().connected());

            bus.publish(new SyncEvent("srv-1", new BankChange(BANK, 1, 0, Instant.EPOCH)));
            awaitTrue(() -> bus.status().lastError().contains("no permission"));

            assertThat(bus.status().connected()).isTrue();
        }
    }

    @Test
    void reconnectsAfterTheServerDropsEveryConnection() throws Exception {
        try (FakeRedisServer server = new FakeRedisServer(RedisSyncBusFakeServerTest::reply);
             RedisSyncBus bus = new RedisSyncBus(settings(server.port()), quietLogger())) {
            List<SyncEvent> received = new CopyOnWriteArrayList<>();
            bus.subscribe(received::add);
            awaitTrue(() -> bus.status().connected());

            server.dropClients();
            awaitTrue(() -> !bus.status().connected());
            awaitTrue(() -> !bus.status().lastError().isEmpty()); // l'erreur est enregistree apres le passage a "coupe"
            awaitTrue(() -> bus.status().connected());

            SyncEvent event = new SyncEvent("srv-2", new BankChange(BANK, 9, 0, Instant.ofEpochMilli(9_000)));
            server.push(channelMessage(SyncPayloads.encode(event)));
            awaitTrue(() -> received.size() == 1);
        }
    }

    @Test
    void aSilentSubscriberConnectionIsCheckedByPingAndReplacedWhenTheServerStopsAnswering() throws Exception {
        // Le serveur accepte l'abonnement mais ne repond jamais aux PING.
        try (FakeRedisServer server = new FakeRedisServer(c -> c.get(0).equals("SUBSCRIBE") ? reply(c) : null);
             RedisSyncBus bus = new RedisSyncBus(settings(server.port()), quietLogger(), 100)) {
            awaitTrue(() -> bus.status().connected());

            awaitTrue(() -> server.commands.stream().anyMatch(c -> c.get(0).equals("PING")));
            awaitTrue(() -> !bus.status().connected());

            assertThat(bus.status().lastError()).contains("PING");
        }
    }

    @Test
    void anUnreachableRedisNeverBlocksNorThrows() throws Exception {
        try (RedisSyncBus bus = new RedisSyncBus(settings(1), quietLogger())) {
            SyncEvent event = new SyncEvent("srv-1", new BankChange(BANK, 1, 0, Instant.EPOCH));
            long start = System.nanoTime();

            bus.publish(event);
            bus.subscribe(ignored -> { });
            awaitTrue(() -> !bus.status().lastError().isEmpty());

            assertThat(bus.status().connected()).isFalse();
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(5));
        }
    }

    @Test
    void closeStopsTheThreadsAndFurtherPublishIsRefused() throws Exception {
        FakeRedisServer server = new FakeRedisServer(RedisSyncBusFakeServerTest::reply);
        RedisSyncBus bus = new RedisSyncBus(settings(server.port()), quietLogger());
        awaitTrue(() -> bus.status().connected());

        bus.close();
        server.close();

        assertThat(bus.status().connected()).isFalse();
        assertThat(bus.publish(new SyncEvent("srv-1", new BankChange(BANK, 1, 0, Instant.EPOCH)))).isFalse();
        assertThat(Thread.getAllStackTraces().keySet()).noneMatch(thread -> thread.getName().startsWith("NovaBank-Redis")
                && thread.isAlive());
    }
}
