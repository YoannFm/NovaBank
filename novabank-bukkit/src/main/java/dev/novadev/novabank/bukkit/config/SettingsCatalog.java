package dev.novadev.novabank.bukkit.config;

import dev.novadev.novabank.bukkit.config.Setting.Kind;
import dev.novadev.novabank.config.section.CommandsSectionParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Tous les reglages de {@code config.yml} que le menu des parametres sait modifier, par categorie. Les items et la
 * disposition des boutons du GUI ont leurs propres editeurs et n'y figurent pas. Bornes et valeurs possibles : celles des
 * analyseurs de {@code novabank-config} (la configuration est de toute facon revalidee en entier a l'enregistrement).
 * Sans dependance serveur.
 */
public final class SettingsCatalog {

    private static final long MAX = Long.MAX_VALUE;

    /**
     * Une categorie du menu.
     *
     * @param id       identifiant (cle des textes)
     * @param icon     icone
     * @param settings reglages, dans l'ordre d'affichage
     */
    public record Category(String id, String icon, List<Setting> settings) {

        /**
         * @throws NullPointerException si un champ est nul
         */
        public Category {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(icon, "icon");
            settings = List.copyOf(settings);
        }
    }

    private SettingsCatalog() {
    }

    /** Commandes de NovaBank, puis commande de banque de chaque plugin hote ({@code commands.hosts.<edition>}). */
    private static List<Setting> commands() {
        List<Setting> settings = new ArrayList<>(List.of(
                Setting.of("commands.player.name", Kind.TEXT, "oak_sign", true),
                Setting.of("commands.player.aliases", Kind.TEXT_LIST, "oak_sign", true),
                Setting.of("commands.admin.name", Kind.TEXT, "command_block", true),
                Setting.of("commands.admin.aliases", Kind.TEXT_LIST, "command_block", true)));
        for (String edition : CommandsSectionParser.HOST_DEFAULTS.keySet()) {
            settings.add(Setting.of("commands.hosts." + edition + ".name", Kind.TEXT, "birch_sign", true));
            settings.add(Setting.of("commands.hosts." + edition + ".aliases", Kind.TEXT_LIST, "birch_sign", true));
        }
        return settings;
    }

    /**
     * @param rankedTypes types de banques actifs dans le brouillon (un bloc de classement par type)
     * @return les categories, dans l'ordre du menu
     */
    public static List<Category> categories(List<String> rankedTypes) {
        List<Setting> leaderboards = new ArrayList<>();
        for (String type : rankedTypes) {
            String base = "leaderboards." + type + ".";
            leaderboards.add(Setting.of(base + "enabled", Kind.BOOLEAN, "lever", false));
            leaderboards.add(Setting.integer(base + "size", "paper", false, 1, 1_000, 5));
            leaderboards.add(Setting.integer(base + "refresh-seconds", "clock", false, 5, 86_400, 30));
            leaderboards.add(Setting.of(base + "per-category", Kind.BOOLEAN, "bookshelf", false));
        }
        return List.of(
                new Category("general", "writable_book", List.of(
                        Setting.choice("language", "book", false, "fr", "en"),
                        Setting.of("server.id", Kind.TEXT, "name_tag", true),
                        Setting.of("server.group", Kind.TEXT, "name_tag", true),
                        Setting.of("gui.filler", Kind.TEXT, "gray_stained_glass_pane", false))),
                new Category("banks", "chest", List.of(
                        Setting.of("banks.entity-types", Kind.TYPES, "chest", false),
                        Setting.of("banks.default-priority", Kind.PRIORITY, "compass", false),
                        Setting.choice("banks.valuation-mode", "gold_ingot", false, "DERIVED", "ACCUMULATED"),
                        Setting.choice("banks.undelivered-policy", "hopper", true, "PENDING_DELIVERY", "RETURN_TO_BANK"),
                        Setting.of("banks.roles", Kind.ROLES, "shield", false))),
                new Category("limits", "clock", List.of(
                        Setting.integer("limits.max-quantity-per-operation", "hopper", false, 1, MAX, 64),
                        Setting.integer("limits.cooldown-ms", "clock", false, 0, MAX, 100),
                        Setting.of("security.save-player-data", Kind.BOOLEAN, "ender_chest", false),
                        Setting.integer("security.shutdown-drain-timeout-ms", "redstone", false, 0, 60_000, 1_000))),
                new Category("leaderboards", "gold_block", leaderboards),
                new Category("integrations", "grass_block", List.of(
                        Setting.of("integrations.superiorskyblock2.island-level", Kind.BOOLEAN, "grass_block", false),
                        Setting.choice("integrations.superiorskyblock2.apply-to", "experience_bottle", false, "worth",
                                "level"),
                        new Setting("integrations.superiorskyblock2.multiplier", Kind.DECIMAL, "anvil", false, List.of(),
                                0, MAX, 0),
                        Setting.of("integrations.bentobox.island-level", Kind.BOOLEAN, "oak_sapling", false),
                        new Setting("integrations.bentobox.multiplier", Kind.DECIMAL, "anvil", false, List.of(),
                                0, MAX, 0),
                        Setting.of("discord.enabled", Kind.BOOLEAN, "note_block", false),
                        Setting.choice("discord.mode", "comparator", false, "webhook", "bot"),
                        Setting.of("discord.webhook-url", Kind.SECRET, "iron_door", false),
                        Setting.of("discord.bot-token", Kind.SECRET, "iron_door", false),
                        Setting.of("discord.channel-id", Kind.TEXT, "name_tag", false),
                        Setting.of("discord.events", Kind.TEXT_LIST, "writable_book", false),
                        Setting.of("discord.bank-types", Kind.TEXT_LIST, "chest", false),
                        Setting.integer("discord.min-quantity", "hopper", false, 1, MAX, 1),
                        Setting.integer("discord.batch-seconds", "clock", false, 1, 3600, 1))),
                new Category("diagnostics", "spyglass", List.of(
                        Setting.of("diagnostics.debug", Kind.BOOLEAN, "spyglass", false),
                        Setting.of("diagnostics.log-operations", Kind.BOOLEAN, "writable_book", false))),
                new Category("commands", "command_block", commands()),
                new Category("storage", "barrel", List.of(
                        Setting.choice("storage.type", "barrel", true, "SQLITE", "YAML", "JSON", "MYSQL", "MARIADB",
                                "MONGODB"),
                        Setting.of("storage.sql.host", Kind.TEXT, "compass", true),
                        Setting.integer("storage.sql.port", "tripwire_hook", true, 1, 65_535, 1),
                        Setting.of("storage.sql.database", Kind.TEXT, "bookshelf", true),
                        Setting.of("storage.sql.username", Kind.TEXT, "player_head", true),
                        Setting.of("storage.sql.password", Kind.SECRET, "iron_door", true),
                        Setting.integer("storage.sql.pool.max-size", "chain", true, 1, 100, 1),
                        Setting.integer("storage.sql.pool.min-idle", "chain", true, 0, 100, 1),
                        Setting.integer("storage.sql.pool.connection-timeout-ms", "clock", true, 250, 600_000, 1_000),
                        Setting.integer("storage.sql.pool.max-lifetime-ms", "clock", true, 30_000, 86_400_000, 60_000),
                        Setting.of("storage.sql.options", Kind.TEXT_MAP, "comparator", true),
                        Setting.of("storage.sqlite.file", Kind.TEXT, "paper", true),
                        Setting.of("storage.file.directory", Kind.TEXT, "chest", true),
                        Setting.of("storage.mongodb.uri", Kind.SECRET, "iron_door", true),
                        Setting.of("storage.mongodb.database", Kind.TEXT, "bookshelf", true),
                        Setting.integer("storage.mongodb.connect-timeout-ms", "clock", true, 100, 300_000, 1_000),
                        Setting.integer("storage.mongodb.server-selection-timeout-ms", "clock", true, 100, 300_000, 1_000),
                        Setting.integer("storage.mongodb.socket-timeout-ms", "clock", true, 100, 300_000, 1_000),
                        Setting.integer("storage.save-interval-seconds", "clock", true, 1, 86_400, 30),
                        Setting.of("storage.drivers.directory", Kind.TEXT, "chest", true))),
                new Category("redis", "ender_eye", List.of(
                        Setting.of("redis.enabled", Kind.BOOLEAN, "lever", true),
                        Setting.of("redis.host", Kind.TEXT, "compass", true),
                        Setting.integer("redis.port", "tripwire_hook", true, 1, 65_535, 1),
                        Setting.of("redis.password", Kind.SECRET, "iron_door", true),
                        Setting.integer("redis.database", "bookshelf", true, 0, Integer.MAX_VALUE, 1),
                        Setting.of("redis.ssl", Kind.BOOLEAN, "shield", true),
                        Setting.of("redis.channel-prefix", Kind.TEXT, "name_tag", true),
                        Setting.integer("redis.connect-timeout-ms", "clock", true, 100, 60_000, 500),
                        Setting.integer("redis.socket-timeout-ms", "clock", true, 100, 60_000, 500),
                        Setting.of("redis.server-name", Kind.TEXT, "name_tag", true),
                        Setting.integer("redis.poll-interval-seconds", "clock", true, 1, 300, 1),
                        Setting.integer("redis.overlap-seconds", "clock", true, 5, 600, 5),
                        Setting.of("redis.deploy-config", Kind.BOOLEAN, "ender_pearl", false))));
    }
}
