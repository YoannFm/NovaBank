package dev.novadev.novabank.config.section;

import dev.novadev.novabank.api.operation.OperationType;
import dev.novadev.novabank.config.model.DiscordSettings;
import dev.novadev.novabank.config.model.Secret;
import dev.novadev.novabank.config.tree.ConfigNode;
import dev.novadev.novabank.core.text.ConsoleText;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Section {@code discord}.
 */
public final class DiscordSectionParser {

    /** Adresse d'un webhook Discord (discord.com, ptb, canary ou l'ancien discordapp.com). */
    static final Pattern WEBHOOK = Pattern.compile(
            "https://(?:(?:ptb|canary)\\.)?discord(?:app)?\\.com/api(?:/v\\d+)?/webhooks/\\d+/[A-Za-z0-9_-]+");
    /** Identifiant Discord (snowflake). */
    static final Pattern SNOWFLAKE = Pattern.compile("\\d{15,21}");
    private static final List<String> EVENTS = List.of("deposit", "withdraw", "reset");

    private DiscordSectionParser() {
    }

    /**
     * Lit et valide la section. Les champs propres a un mode ne sont exiges que si l'envoi est actif dans ce mode.
     *
     * @param root racine de la configuration
     * @return la section (desactivee si elle est absente)
     */
    public static DiscordSettings parse(ConfigNode root) {
        ConfigNode node = root.section("discord");
        boolean enabled = node.bool("enabled", false);
        DiscordSettings.Mode mode = node.enumValue("mode", DiscordSettings.Mode.class, DiscordSettings.Mode.WEBHOOK);
        Secret webhook = node.secret("webhook-url", "");
        Secret token = node.secret("bot-token", "");
        String channel = node.string("channel-id", "").trim();
        if (enabled && mode == DiscordSettings.Mode.WEBHOOK && !WEBHOOK.matcher(webhook.reveal().trim()).matches()) {
            node.error("webhook-url", ConsoleText.tr(
                    "adresse de webhook Discord attendue (https://discord.com/api/webhooks/<id>/<jeton>)"));
        }
        if (enabled && mode == DiscordSettings.Mode.BOT) {
            if (token.isBlank()) {
                node.error("bot-token",
                        ConsoleText.tr("jeton du bot attendu (portail des developpeurs Discord, onglet Bot)"));
            }
            if (!SNOWFLAKE.matcher(channel).matches()) {
                node.error("channel-id", ConsoleText.tr("identifiant de salon Discord attendu (nombre), recu : {0}",
                        channel.isEmpty() ? "-" : channel));
            }
        }
        Set<OperationType> events = EnumSet.noneOf(OperationType.class);
        List<String> names = node.stringList("events", List.of("deposit", "withdraw"));
        for (String name : names) {
            String lower = name.trim().toLowerCase(Locale.ROOT);
            if (EVENTS.contains(lower)) {
                events.add(OperationType.valueOf(lower.toUpperCase(Locale.ROOT)));
            } else {
                node.error("events", ConsoleText.tr("evenement inconnu '{0}' (possibles : deposit, withdraw, reset)",
                        name));
            }
        }
        List<String> bankTypes = node.stringList("bank-types", List.of()).stream()
                .map(type -> type.trim().toLowerCase(Locale.ROOT)).filter(type -> !type.isEmpty()).toList();
        return new DiscordSettings(enabled, mode, Secret.of(webhook.reveal().trim()), Secret.of(token.reveal().trim()),
                channel, events, bankTypes, node.longValue("min-quantity", 1, 1, Long.MAX_VALUE),
                node.intValue("batch-seconds", 5, 1, 3600));
    }
}
