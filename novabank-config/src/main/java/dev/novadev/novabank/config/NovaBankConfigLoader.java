package dev.novadev.novabank.config;

import dev.novadev.novabank.config.catalog.ItemAvailability;
import dev.novadev.novabank.config.migration.ConfigMigrator;
import dev.novadev.novabank.config.model.BankSettings;
import dev.novadev.novabank.config.model.StorageSettings;
import dev.novadev.novabank.config.section.BanksSectionParser;
import dev.novadev.novabank.config.section.CommandsSectionParser;
import dev.novadev.novabank.config.section.DiagnosticsSectionParser;
import dev.novadev.novabank.config.section.DiscordSectionParser;
import dev.novadev.novabank.config.section.IntegrationsSectionParser;
import dev.novadev.novabank.config.section.GuiSectionParser;
import dev.novadev.novabank.config.section.ItemsSectionParser;
import dev.novadev.novabank.config.section.LanguageParser;
import dev.novadev.novabank.config.section.LeaderboardsSectionParser;
import dev.novadev.novabank.config.section.LimitsSectionParser;
import dev.novadev.novabank.config.section.RedisSectionParser;
import dev.novadev.novabank.config.section.SecuritySectionParser;
import dev.novadev.novabank.config.section.ServerSectionParser;
import dev.novadev.novabank.config.section.StorageSectionParser;
import dev.novadev.novabank.config.tree.ConfigNode;
import dev.novadev.novabank.config.tree.ConfigTrees;
import dev.novadev.novabank.config.tree.ParseContext;
import dev.novadev.novabank.config.validation.ValidationReport;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Charge une configuration depuis l'arbre generique produit par la plateforme
 * (YamlConfiguration / SnakeYAML) : migration de schema, lecture de chaque section,
 * validation, signalement des cles inconnues.
 *
 * <p>Ne leve jamais d'exception pour une configuration invalide : toutes les issues
 * sont accumulees dans le rapport. Sans etat, utilisable depuis plusieurs threads si
 * le port {@link ItemAvailability} l'est.
 */
public final class NovaBankConfigLoader {

    private final ItemAvailability availability;
    private final ConfigMigrator migrator;

    /**
     * Chargeur du schema courant.
     *
     * @param availability items disponibles sur ce serveur
     */
    public NovaBankConfigLoader(ItemAvailability availability) {
        this(availability, ConfigMigrator.standard());
    }

    /**
     * Chargeur avec une chaine de migration explicite.
     *
     * @param availability items disponibles sur ce serveur
     * @param migrator     migration de schema appliquee avant la lecture
     */
    public NovaBankConfigLoader(ItemAvailability availability, ConfigMigrator migrator) {
        this.availability = Objects.requireNonNull(availability, "availability");
        this.migrator = Objects.requireNonNull(migrator, "migrator");
    }

    /**
     * Charge et valide un arbre.
     *
     * @param tree arbre lu depuis le fichier, inchange
     * @return la configuration (si aucune erreur), le rapport et l'arbre migre
     */
    public ConfigLoadResult load(Map<?, ?> tree) {
        return evaluate(tree).result();
    }

    /**
     * Chargement detaille, qui expose aussi les chemins reconnus (utilise par
     * {@link ConfigEditor} pour refuser un chemin inconnu).
     */
    Evaluation evaluate(Map<?, ?> tree) {
        ValidationReport.Builder migrationIssues = ValidationReport.builder();
        Optional<ConfigMigrator.Migration> migration = migrator.migrate(tree, migrationIssues);
        ParseContext context = new ParseContext();
        context.report().addAll(migrationIssues.build());
        if (migration.isEmpty()) {
            ConfigLoadResult failed = new ConfigLoadResult(Optional.empty(), context.report().build(),
                    ConfigTrees.immutableCopy(tree), false);
            return new Evaluation(failed, context);
        }
        Map<String, Object> migrated = migration.get().tree();
        NovaBankConfig config = parse(ConfigNode.root(migrated, context));
        context.reportUnreadKeys();
        ValidationReport report = context.report().build();
        boolean wasMigrated = migration.get().sourceVersion() < migrator.targetVersion();
        Optional<NovaBankConfig> valid = report.hasErrors() ? Optional.empty() : Optional.of(config);
        return new Evaluation(new ConfigLoadResult(valid, report, migrated, wasMigrated), context);
    }

    private NovaBankConfig parse(ConfigNode root) {
        // Deja valide par le migrateur ; lu ici pour etre reconnu.
        root.intValue(ConfigMigrator.VERSION_KEY, migrator.targetVersion(), 1, Integer.MAX_VALUE);
        StorageSettings storage = StorageSectionParser.parse(root);
        BankSettings banks = BanksSectionParser.parse(root);
        return new NovaBankConfig(
                ServerSectionParser.parse(root),
                storage,
                RedisSectionParser.parse(root, storage.type()),
                banks,
                ItemsSectionParser.parse(root, availability),
                LimitsSectionParser.parse(root),
                LeaderboardsSectionParser.parse(root, banks.entityTypes()),
                DiagnosticsSectionParser.parse(root),
                SecuritySectionParser.parse(root),
                CommandsSectionParser.parse(root),
                LanguageParser.parse(root),
                GuiSectionParser.parse(root, availability),
                IntegrationsSectionParser.parse(root),
                DiscordSectionParser.parse(root));
    }

    /**
     * Resultat d'un chargement et etat de lecture associe.
     *
     * @param result  resultat public
     * @param context chemins lus et issues
     */
    record Evaluation(ConfigLoadResult result, ParseContext context) {
    }
}
