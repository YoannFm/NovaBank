package dev.novadev.novabank.config;

import static dev.novadev.novabank.config.TestConfigs.loadValid;
import static dev.novadev.novabank.config.TestConfigs.minimal;
import static dev.novadev.novabank.config.TestConfigs.minimalWith;
import static dev.novadev.novabank.config.TestConfigs.singleError;
import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.api.operation.OperationType;
import dev.novadev.novabank.config.model.DiscordSettings;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DiscordSectionTest {

    private static final String WEBHOOK = "https://discord.com/api/webhooks/123456789012345678/abcDEF-ghi_jkl";

    @Test
    void section_absente_desactivee() {
        assertThat(loadValid(minimal()).discord().enabled()).isFalse();
    }

    @Test
    void webhook_valide_lu() {
        DiscordSettings read = loadValid(minimalWith("discord", Map.of("enabled", true, "webhook-url", WEBHOOK,
                "events", List.of("deposit", "reset"), "bank-types", List.of("Island"), "batch-seconds", 10))).discord();

        assertThat(read.mode()).isEqualTo(DiscordSettings.Mode.WEBHOOK);
        assertThat(read.webhookUrl().reveal()).isEqualTo(WEBHOOK);
        assertThat(read.events()).containsExactlyInAnyOrder(OperationType.DEPOSIT, OperationType.RESET);
        assertThat(read.bankTypes()).containsExactly("island");
        assertThat(read.batchSeconds()).isEqualTo(10);
    }

    @Test
    void bot_valide_lu() {
        DiscordSettings read = loadValid(minimalWith("discord", Map.of("enabled", true, "mode", "BOT",
                "bot-token", "jeton", "channel-id", "123456789012345678"))).discord();

        assertThat(read.mode()).isEqualTo(DiscordSettings.Mode.BOT);
        assertThat(read.channelId()).isEqualTo("123456789012345678");
    }

    @Test
    void reglages_incoherents_refuses_seulement_si_actif() {
        singleError(minimalWith("discord", Map.of("enabled", true, "webhook-url", "https://exemple.net/hook")),
                "discord.webhook-url");
        singleError(minimalWith("discord", Map.of("enabled", true, "mode", "BOT", "bot-token", "jeton",
                "channel-id", "salon")), "discord.channel-id");
        singleError(minimalWith("discord", Map.of("events", List.of("deposit", "vol"))), "discord.events");
        assertThat(loadValid(minimalWith("discord", Map.of("enabled", false, "webhook-url", "x"))).discord().enabled())
                .isFalse();
    }
}
