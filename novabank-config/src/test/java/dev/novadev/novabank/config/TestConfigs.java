package dev.novadev.novabank.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.config.migration.ConfigMigrator;
import dev.novadev.novabank.config.tree.ConfigTrees;
import dev.novadev.novabank.config.validation.ValidationIssue;
import java.util.List;
import java.util.Map;

/**
 * Arbres de test et raccourcis de chargement.
 */
public final class TestConfigs {

    private TestConfigs() {
    }

    /** Plus petite configuration valide sans avertissement : version du schema et un item. */
    public static Map<String, Object> minimal() {
        return ConfigTrees.immutableCopy(Map.of(
                "config-version", ConfigMigrator.LATEST_VERSION,
                "items", List.of(Map.of("key", "minecraft:stone", "price", "1"))));
    }

    public static Map<String, Object> with(Map<?, ?> tree, String path, Object value) {
        return ConfigTrees.withValue(tree, path, value).orElseThrow();
    }

    public static Map<String, Object> minimalWith(String path, Object value) {
        return with(minimal(), path, value);
    }

    public static ConfigLoadResult load(Map<?, ?> tree) {
        return new NovaBankConfigLoader(FakeItemAvailability.everything()).load(tree);
    }

    public static NovaBankConfig loadValid(Map<?, ?> tree) {
        ConfigLoadResult result = load(tree);
        assertThat(result.report().errors()).isEmpty();
        return result.config().orElseThrow();
    }

    public static List<String> errorPaths(ConfigLoadResult result) {
        return result.report().errors().stream().map(ValidationIssue::path).toList();
    }

    public static List<String> warningPaths(ConfigLoadResult result) {
        return result.report().warnings().stream().map(ValidationIssue::path).toList();
    }

    /** Charge, verifie le refus et l'unique erreur au chemin attendu. */
    public static ValidationIssue singleError(Map<?, ?> tree, String path) {
        ConfigLoadResult result = load(tree);
        assertThat(result.config()).isEmpty();
        assertThat(errorPaths(result)).containsExactly(path);
        return result.report().errors().get(0);
    }
}
