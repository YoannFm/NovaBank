package dev.novadev.novabank.config.migration;

import dev.novadev.novabank.config.tree.ConfigNode;
import dev.novadev.novabank.config.tree.ConfigTrees;
import dev.novadev.novabank.config.tree.ParseContext;
import dev.novadev.novabank.config.validation.ValidationReport;
import dev.novadev.novabank.core.text.ConsoleText;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Amene un arbre de configuration a la version de schema courante en enchainant les
 * {@link ConfigMigration}.
 */
public final class ConfigMigrator {

    /** Cle de la version du schema. */
    public static final String VERSION_KEY = "config-version";

    /** Version du schema lue par cette version du plugin. */
    public static final int LATEST_VERSION = 7;

    private final int targetVersion;
    private final Map<Integer, ConfigMigration> steps = new HashMap<>();

    /**
     * Cree un migrateur.
     *
     * @param targetVersion version cible (au moins 1)
     * @param migrations    une etape pour chaque version de 1 a {@code targetVersion - 1}
     * @throws IllegalArgumentException si la chaine est incomplete, en double ou hors bornes
     */
    public ConfigMigrator(int targetVersion, List<ConfigMigration> migrations) {
        if (targetVersion < 1) {
            throw new IllegalArgumentException(ConsoleText.tr("Version cible invalide : {0}", targetVersion));
        }
        this.targetVersion = targetVersion;
        for (ConfigMigration migration : migrations) {
            int from = migration.fromVersion();
            if (from < 1 || from >= targetVersion) {
                throw new IllegalArgumentException(ConsoleText.tr("Migration hors bornes : depuis la version {0}",
                        from));
            }
            if (steps.putIfAbsent(from, migration) != null) {
                throw new IllegalArgumentException(ConsoleText.tr("Deux migrations depuis la version {0}", from));
            }
        }
        for (int version = 1; version < targetVersion; version++) {
            if (!steps.containsKey(version)) {
                throw new IllegalArgumentException(ConsoleText.tr("Migration manquante depuis la version {0}",
                        version));
            }
        }
    }

    /**
     * Migrateur du schema courant du plugin.
     *
     * @return le migrateur vers {@link #LATEST_VERSION}
     */
    public static ConfigMigrator standard() {
        return new ConfigMigrator(LATEST_VERSION, List.of(new AddedKeysMigration(1),
                new RemovedKeysMigration(2, Set.of("license")), new AddedKeysMigration(3),
                new AddedKeysMigration(4), new AddedKeysMigration(5),
                new AddedKeysMigration(6)));
    }

    /**
     * Version produite par ce migrateur.
     *
     * @return la version cible
     */
    public int targetVersion() {
        return targetVersion;
    }

    /**
     * Cles retirees par les etapes appliquees a un fichier de cette version, pour les retirer
     * aussi du fichier lors de sa mise a jour.
     *
     * @param version version du fichier (en dessous de 1 : comme 1)
     * @return chemins retires par les etapes de {@code version} a la version cible
     */
    public Set<String> removedKeysSince(int version) {
        Set<String> removed = new HashSet<>();
        for (int step = Math.max(version, 1); step < targetVersion; step++) {
            removed.addAll(steps.get(step).removedKeys());
        }
        return Set.copyOf(removed);
    }

    /**
     * Migre un arbre. Version absente : version cible supposee, avec avertissement.
     * Version invalide ou plus recente que la cible, ou etape en echec : erreur et
     * resultat vide (l'arbre ne peut pas etre lu de facon fiable).
     *
     * @param tree   arbre source, inchange
     * @param report destination des issues
     * @return l'arbre migre (non modifiable, {@code config-version} a jour), ou vide
     */
    public Optional<Migration> migrate(Map<?, ?> tree, ValidationReport.Builder report) {
        Optional<Integer> source = sourceVersion(tree, report);
        if (source.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> current = ConfigTrees.mutableCopy(tree);
        for (int version = source.get(); version < targetVersion; version++) {
            try {
                current = ConfigTrees.mutableCopy(steps.get(version).migrate(current));
            } catch (RuntimeException e) {
                report.error(VERSION_KEY, ConsoleText.tr("echec de la migration de la version {0} vers {1} : {2}",
                        version, (version + 1), e.getMessage()));
                return Optional.empty();
            }
        }
        current.put(VERSION_KEY, targetVersion);
        return Optional.of(new Migration(ConfigTrees.immutableCopy(current), source.get()));
    }

    private Optional<Integer> sourceVersion(Map<?, ?> tree, ValidationReport.Builder report) {
        ParseContext context = new ParseContext();
        ConfigNode root = ConfigNode.root(tree, context);
        if (!root.has(VERSION_KEY)) {
            report.warning(VERSION_KEY, ConsoleText.tr("absente : version {0} supposee", targetVersion));
            return Optional.of(targetVersion);
        }
        int version = root.intValue(VERSION_KEY, 0, 1, Integer.MAX_VALUE);
        ValidationReport issues = context.report().build();
        report.addAll(issues);
        if (issues.hasErrors()) {
            return Optional.empty();
        }
        if (version > targetVersion) {
            report.error(VERSION_KEY,
                    ConsoleText.tr("version {0} plus recente que celle geree par ce plugin ({1}) : mettez NovaBank a jour",
                            version, targetVersion));
            return Optional.empty();
        }
        return Optional.of(version);
    }

    /**
     * Resultat d'une migration.
     *
     * @param tree          arbre en version cible, non modifiable
     * @param sourceVersion version lue avant migration
     */
    public record Migration(Map<String, Object> tree, int sourceVersion) {
    }
}
