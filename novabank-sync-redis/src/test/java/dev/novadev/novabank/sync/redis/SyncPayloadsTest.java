package dev.novadev.novabank.sync.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.core.sync.BankChange;
import dev.novadev.novabank.core.sync.SyncEvent;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SyncPayloadsTest {

    private static final BankId BANK = new BankId(UUID.fromString("00000000-0000-0000-0000-0000000000aa"));

    @Test
    void roundTripKeepsEveryField() {
        SyncEvent event = new SyncEvent("lobby-1:a3f", new BankChange(BANK, 42, 3, Instant.ofEpochMilli(1_793_000_000_123L)));

        assertThat(SyncPayloads.decode(SyncPayloads.encode(event))).contains(event);
    }

    @Test
    void anOriginWithTheSeparatorIsRefusedAtEncodingTime() {
        SyncEvent event = new SyncEvent("a|b", new BankChange(BANK, 1, 0, Instant.EPOCH));

        assertThatThrownBy(() -> SyncPayloads.encode(event)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "garbage", "2|srv|00000000-0000-0000-0000-0000000000aa|1|0|1",
            "1|srv|not-a-uuid|1|0|1", "1|srv|00000000-0000-0000-0000-0000000000aa|x|0|1",
            "1|srv|00000000-0000-0000-0000-0000000000aa|-1|0|1", "1|srv|00000000-0000-0000-0000-0000000000aa|1|0",
            "1||00000000-0000-0000-0000-0000000000aa|1|0|1", "1|srv|00000000-0000-0000-0000-0000000000aa|1|0|1|x",
            "1|srv|00000000-0000-0000-0000-0000000000aa|1|0|99999999999999999999"})
    void malformedMessagesAreIgnored(String payload) {
        assertThat(SyncPayloads.decode(payload)).isEmpty();
    }

    @Test
    void controlMessagesRoundTripAndRejectGarbage() {
        assertThat(SyncPayloads.decodeControl(SyncPayloads.encodeControl("srv-1", "reload")))
                .hasValueSatisfying(fields -> assertThat(fields).containsExactly("srv-1", "reload"));
        assertThat(SyncPayloads.decodeControl("1|srv-1|RELOAD")).isEmpty();
        assertThat(SyncPayloads.decodeControl("1|srv-1")).isEmpty();
        assertThatThrownBy(() -> SyncPayloads.encodeControl("srv-1", "re|load")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void controlMessageCanCarryAConfigPayloadButNotAnOversizedOrIllFormedOne() {
        String payload = "eJxLTEoFAAKNAV8_-"; // base64url
        assertThat(SyncPayloads.decodeControl(SyncPayloads.encodeControl("srv-1", "config:" + payload)))
                .hasValueSatisfying(fields -> assertThat(fields).containsExactly("srv-1", "config:" + payload));
        assertThat(SyncPayloads.encodeControl("srv-1", "config:" + "A".repeat(700_000))).hasSizeGreaterThan(700_000);
        assertThatThrownBy(() -> SyncPayloads.encodeControl("srv-1", "config:" + "A".repeat(800_001)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SyncPayloads.encodeControl("srv-1", "config:a|b")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SyncPayloads.encodeControl("srv-1", "config:")).isInstanceOf(IllegalArgumentException.class);
    }
}
