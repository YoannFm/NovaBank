package dev.novadev.novabank.sync.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class RespConnectionTest {

    private static RedisConnectionSettings settings(int port, String password) {
        return new RedisConnectionSettings("127.0.0.1", port, password, false, "nb", 1_000, 1_000);
    }

    private static Object roundTrip(Function<List<String>, byte[]> reply) throws Exception {
        try (FakeRedisServer server = new FakeRedisServer(reply);
             RespConnection connection = RespConnection.open(settings(server.port(), ""), 1_000)) {
            connection.send("ANY");
            return connection.read();
        }
    }

    @Test
    void parsesEveryReplyType() throws Exception {
        assertThat(roundTrip(c -> FakeRedisServer.simple("OK"))).isEqualTo("OK");
        assertThat(roundTrip(c -> FakeRedisServer.integer(7))).isEqualTo(7L);
        assertThat(roundTrip(c -> FakeRedisServer.bulk("héllo"))).isEqualTo("héllo");
        assertThat(roundTrip(c -> "$-1\r\n".getBytes())).isNull();
        assertThat(roundTrip(c -> FakeRedisServer.array(FakeRedisServer.bulk("message"), FakeRedisServer.bulk("ch"),
                FakeRedisServer.bulk("payload")))).isEqualTo(List.of("message", "ch", "payload"));
    }

    @Test
    void sendsCommandsAsBulkStringArrays() throws Exception {
        try (FakeRedisServer server = new FakeRedisServer(c -> FakeRedisServer.simple("OK"));
             RespConnection connection = RespConnection.open(settings(server.port(), ""), 1_000)) {
            connection.send("PUBLISH", "nb:bank-changes", "un message élégant");
            connection.read();

            assertThat(server.commands).containsExactly(List.of("PUBLISH", "nb:bank-changes", "un message élégant"));
        }
    }

    @Test
    void authenticatesWithThePasswordAndNeverLeaksItWhenRefused() throws Exception {
        try (FakeRedisServer server = new FakeRedisServer(c -> "AUTH".equals(c.get(0))
                && "s3cret".equals(c.get(1)) ? FakeRedisServer.simple("OK") : FakeRedisServer.error("WRONGPASS invalid"))) {
            RespConnection ok = RespConnection.open(settings(server.port(), "s3cret"), 1_000);
            ok.close();
            assertThat(server.commands).contains(List.of("AUTH", "s3cret"));
            assertThatThrownBy(() -> RespConnection.open(settings(server.port(), "wrong"), 1_000))
                    .isInstanceOf(IOException.class).hasMessageContaining("WRONGPASS").hasMessageNotContaining("wrong\"");
        }
    }

    @Test
    void aServerErrorBecomesAProtocolException() {
        assertThatThrownBy(() -> roundTrip(c -> FakeRedisServer.error("ERR boom")))
                .isInstanceOf(RedisProtocolException.class).hasMessageContaining("ERR boom");
    }

    @Test
    void invalidOrOversizedFramesAreRefused() {
        assertThatThrownBy(() -> roundTrip(c -> "?junk\r\n".getBytes())).isInstanceOf(RedisProtocolException.class);
        assertThatThrownBy(() -> roundTrip(c -> "$99999999\r\n".getBytes())).isInstanceOf(RedisProtocolException.class);
        assertThatThrownBy(() -> roundTrip(c -> "*100000\r\n".getBytes())).isInstanceOf(RedisProtocolException.class);
        assertThatThrownBy(() -> roundTrip(c -> ":notanumber\r\n".getBytes())).isInstanceOf(RedisProtocolException.class);
        assertThatThrownBy(() -> roundTrip(c -> "$5\r\nabc".getBytes())).isInstanceOf(IOException.class);
    }

    @Test
    void anIdleTimeoutBeforeAnyByteIsReportedWithoutBreakingTheConnection() throws Exception {
        try (FakeRedisServer server = new FakeRedisServer(c -> null);
             RespConnection connection = RespConnection.open(settings(server.port(), ""), 150)) {
            assertThat(connection.readOrTimeout()).isSameAs(RespConnection.TIMEOUT);
            assertThat(connection.readOrTimeout()).isSameAs(RespConnection.TIMEOUT);
        }
    }

    @Test
    void connectionRefusedIsAnIoException() {
        assertThatThrownBy(() -> RespConnection.open(settings(1, ""), 500)).isInstanceOf(IOException.class);
    }

    @Test
    void settingsNeverShowThePassword() {
        RedisConnectionSettings settings = settings(6379, "s3cret");

        assertThat(settings.toString()).doesNotContain("s3cret").contains("127.0.0.1:6379");
        assertThat(settings.describe()).doesNotContain("s3cret");
        assertThat(settings.channel()).isEqualTo("nb:bank-changes");
    }
}
