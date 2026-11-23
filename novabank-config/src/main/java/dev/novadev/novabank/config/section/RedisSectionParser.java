package dev.novadev.novabank.config.section;

import static dev.novadev.novabank.config.section.ConfigPatterns.NOT_BLANK;
import static dev.novadev.novabank.config.section.ConfigPatterns.NOT_BLANK_HINT;

import dev.novadev.novabank.config.model.RedisSettings;
import dev.novadev.novabank.config.model.StorageType;
import dev.novadev.novabank.config.tree.ConfigNode;
import dev.novadev.novabank.core.text.ConsoleText;
import java.util.regex.Pattern;

/**
 * Section {@code redis}. Regle inter-sections (D4) : Redis active avec un stockage
 * mono-serveur (YAML, JSON, SQLite) est une erreur bloquante.
 */
public final class RedisSectionParser {

    private static final Pattern CHANNEL_PREFIX = Pattern.compile("[a-z0-9_.:-]+");
    private static final Pattern SERVER_NAME = Pattern.compile("[A-Za-z0-9._-]{1,32}");

    private RedisSectionParser() {
    }

    /**
     * Lit et valide la section.
     *
     * @param root        racine de la configuration
     * @param storageType backend de stockage actif
     * @return la section
     */
    public static RedisSettings parse(ConfigNode root, StorageType storageType) {
        ConfigNode node = root.section("redis");
        boolean enabled = node.bool("enabled", false);
        if (enabled && storageType.singleServer()) {
            node.error("enabled",
                    ConsoleText.tr("Redis (multi-serveur) est incompatible avec storage.type = {0}, stockage mono-serveur : utilisez MYSQL, MARIADB ou MONGODB, ou desactivez Redis",
                            storageType));
        }
        return new RedisSettings(enabled,
                node.matching("host", "localhost", NOT_BLANK, NOT_BLANK_HINT),
                node.intValue("port", 6379, 1, 65_535),
                node.secret("password", ""),
                node.intValue("database", 0, 0, Integer.MAX_VALUE),
                node.bool("ssl", false),
                node.matching("channel-prefix", "novabank", CHANNEL_PREFIX, "minuscules, chiffres, '_', '.', ':' ou '-'"),
                node.intValue("connect-timeout-ms", 2_000, 100, 60_000),
                node.intValue("socket-timeout-ms", 2_000, 100, 60_000),
                node.matching("server-name", "server", SERVER_NAME, "1 a 32 caracteres : lettres, chiffres, '.', '_' ou '-'"),
                node.intValue("poll-interval-seconds", 5, 1, 300),
                node.intValue("overlap-seconds", 30, 5, 600),
                node.bool("deploy-config", true));
    }
}
