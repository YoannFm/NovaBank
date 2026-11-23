package dev.novadev.novabank.config.migration;

import dev.novadev.novabank.config.tree.ConfigTrees;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Etape de version qui retire des cles devenues sans objet, avec leurs sous-cles. Une cle absente
 * est ignoree. Les memes cles sont retirees du fichier de l'administrateur par la plateforme lors
 * de sa mise a jour (voir {@link ConfigMigrator#removedKeysSince(int)}), ce qui evite qu'elles soient
 * signalees comme inconnues a chaque demarrage.
 *
 * <p>Version 2 vers 3 : section {@code license} retiree (NovaBank n'a aucun systeme de licence,
 * decision du 2026-10-28).
 */
final class RemovedKeysMigration implements ConfigMigration {

    private static final Pattern SEPARATOR = Pattern.compile(Pattern.quote(String.valueOf(ConfigTrees.SEPARATOR)));

    private final int fromVersion;
    private final Set<String> removedKeys;

    /**
     * @param fromVersion version de depart
     * @param removedKeys chemins retires, par exemple {@code license} ou {@code limits.ancienne-cle}
     */
    RemovedKeysMigration(int fromVersion, Set<String> removedKeys) {
        this.fromVersion = fromVersion;
        this.removedKeys = Set.copyOf(removedKeys);
    }

    @Override
    public int fromVersion() {
        return fromVersion;
    }

    @Override
    public Set<String> removedKeys() {
        return removedKeys;
    }

    @Override
    public Map<String, Object> migrate(Map<String, Object> tree) {
        removedKeys.forEach(path -> remove(tree, SEPARATOR.split(path, -1)));
        return tree;
    }

    private static void remove(Map<String, Object> tree, String[] segments) {
        Map<?, ?> section = tree;
        for (int index = 0; index < segments.length - 1; index++) {
            if (!(section.get(segments[index]) instanceof Map<?, ?> child)) {
                return;
            }
            section = child;
        }
        section.remove(segments[segments.length - 1]);
    }
}
