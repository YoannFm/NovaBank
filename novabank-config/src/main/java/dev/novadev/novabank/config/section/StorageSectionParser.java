package dev.novadev.novabank.config.section;

import static dev.novadev.novabank.config.section.ConfigPatterns.NOT_BLANK;
import static dev.novadev.novabank.config.section.ConfigPatterns.NOT_BLANK_HINT;

import dev.novadev.novabank.config.model.DriverSettings;
import dev.novadev.novabank.config.model.FileStorageSettings;
import dev.novadev.novabank.config.model.MongoSettings;
import dev.novadev.novabank.config.model.Secret;
import dev.novadev.novabank.config.model.SqliteSettings;
import dev.novadev.novabank.config.model.StorageSettings;
import dev.novadev.novabank.config.model.StorageType;
import dev.novadev.novabank.config.tree.ConfigNode;
import dev.novadev.novabank.core.text.ConsoleText;
import java.util.regex.Pattern;

/**
 * Section {@code storage}. Toutes les sous-sections sont validees, y compris celles
 * du backend inactif (elles servent de cible a une migration entre backends).
 */
public final class StorageSectionParser {

    private static final Pattern MONGO_DATABASE = Pattern.compile("[A-Za-z0-9_-]{1,63}");
    private static final int MONGO_MIN_TIMEOUT_MS = 100;
    private static final int MONGO_MAX_TIMEOUT_MS = 300_000;

    private StorageSectionParser() {
    }

    /**
     * Lit et valide la section.
     *
     * @param root racine de la configuration
     * @return la section
     */
    public static StorageSettings parse(ConfigNode root) {
        ConfigNode node = root.section("storage");
        StorageType type = node.enumValue("type", StorageType.class, StorageType.SQLITE);
        return new StorageSettings(type,
                SqlSectionParser.parse(node.section("sql"), type),
                new SqliteSettings(notBlank(node.section("sqlite"), "file", "data/novabank.db")),
                new FileStorageSettings(notBlank(node.section("file"), "directory", "data")),
                parseMongo(node.section("mongodb")),
                node.intValue("save-interval-seconds", 300, 1, 86_400),
                parseDrivers(node.section("drivers")));
    }

    private static MongoSettings parseMongo(ConfigNode node) {
        Secret uri = node.secret("uri", "mongodb://localhost:27017");
        if (!uri.reveal().startsWith("mongodb://") && !uri.reveal().startsWith("mongodb+srv://")) {
            node.error("uri", ConsoleText.tr("URI MongoDB invalide (attendu : mongodb://... ou mongodb+srv://...)"));
        }
        return new MongoSettings(uri, node.matching("database", "novabank", MONGO_DATABASE,
                "1 a 63 lettres, chiffres, '_' ou '-'"),
                node.intValue("connect-timeout-ms", 5_000, MONGO_MIN_TIMEOUT_MS, MONGO_MAX_TIMEOUT_MS),
                node.intValue("server-selection-timeout-ms", 10_000, MONGO_MIN_TIMEOUT_MS, MONGO_MAX_TIMEOUT_MS),
                node.intValue("socket-timeout-ms", 30_000, MONGO_MIN_TIMEOUT_MS, MONGO_MAX_TIMEOUT_MS));
    }

    private static DriverSettings parseDrivers(ConfigNode node) {
        if (!node.bool("verify-sha256", true)) {
            node.warning("verify-sha256",
                    ConsoleText.tr("la verification SHA-256 des pilotes ne peut pas etre desactivee : valeur ignoree"));
        }
        return new DriverSettings(notBlank(node, "directory", "libs"));
    }

    private static String notBlank(ConfigNode node, String key, String defaultValue) {
        return node.matching(key, defaultValue, NOT_BLANK, NOT_BLANK_HINT);
    }
}
