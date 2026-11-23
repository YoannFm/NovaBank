package dev.novadev.novabank.config.section;

import static dev.novadev.novabank.config.section.ConfigPatterns.NOT_BLANK;
import static dev.novadev.novabank.config.section.ConfigPatterns.NOT_BLANK_HINT;

import dev.novadev.novabank.config.model.PoolSettings;
import dev.novadev.novabank.config.model.Secret;
import dev.novadev.novabank.config.model.SqlSettings;
import dev.novadev.novabank.config.model.StorageType;
import dev.novadev.novabank.config.tree.ConfigNode;
import dev.novadev.novabank.core.text.ConsoleText;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Sous-section {@code storage.sql} et son pool. Les bornes du pool reprennent les
 * minimums acceptes par HikariCP.
 */
final class SqlSectionParser {

    private static final Pattern DATABASE = Pattern.compile("[A-Za-z0-9_]+");
    private static final Set<String> CREDENTIAL_OPTIONS = Set.of("user", "password");

    private SqlSectionParser() {
    }

    static SqlSettings parse(ConfigNode node, StorageType activeType) {
        String host = node.matching("host", "localhost", NOT_BLANK, NOT_BLANK_HINT);
        int port = node.intValue("port", 3306, 1, 65_535);
        String database = node.matching("database", "novabank", DATABASE, "lettres, chiffres ou '_'");
        Secret username = node.secret("username", "novabank");
        Secret password = node.secret("password", "");
        if (activeType.sqlServer()) {
            if (username.isBlank()) {
                node.error("username", ConsoleText.tr("utilisateur SQL vide alors que storage.type = {0}", activeType));
            }
            if (password.isBlank()) {
                node.warning("password",
                        ConsoleText.tr("mot de passe SQL vide : deconseille hors environnement de test"));
            }
        }
        Map<String, String> options = node.stringMap("options");
        for (String key : options.keySet()) {
            if (CREDENTIAL_OPTIONS.contains(key.toLowerCase(Locale.ROOT))) {
                node.error("options",
                        ConsoleText.tr("l'option '{0}' est interdite : identifiants dans username / password uniquement (jamais dans les options JDBC)",
                                key));
            }
        }
        return new SqlSettings(host, port, database, username, password, parsePool(node.section("pool")), options);
    }

    private static PoolSettings parsePool(ConfigNode node) {
        int maxSize = node.intValue("max-size", 10, 1, 100);
        int minIdle = node.intValue("min-idle", 2, 0, 100);
        if (minIdle > maxSize) {
            node.error("min-idle", ConsoleText.tr("doit etre inferieur ou egal a max-size ({0})", maxSize));
        }
        long connectionTimeoutMs = node.longValue("connection-timeout-ms", 30_000, 250, 600_000);
        long maxLifetimeMs = node.longValue("max-lifetime-ms", 1_800_000, 30_000, 86_400_000);
        return new PoolSettings(maxSize, minIdle, connectionTimeoutMs, maxLifetimeMs);
    }
}
