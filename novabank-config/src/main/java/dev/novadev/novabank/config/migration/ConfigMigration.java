package dev.novadev.novabank.config.migration;

import java.util.Map;
import java.util.Set;

/**
 * Etape de migration du schema de configuration, de {@link #fromVersion()} vers
 * {@code fromVersion() + 1}.
 */
public interface ConfigMigration {

    /**
     * Version du schema lue par cette etape.
     *
     * @return la version source (au moins 1)
     */
    int fromVersion();

    /**
     * Transforme l'arbre. L'etape ne modifie pas {@code config-version} : le
     * {@link ConfigMigrator} s'en charge.
     *
     * @param tree copie modifiable de l'arbre en version {@link #fromVersion()}
     * @return l'arbre en version {@code fromVersion() + 1} (eventuellement {@code tree} lui-meme)
     */
    Map<String, Object> migrate(Map<String, Object> tree);

    /**
     * Cles retirees par cette etape, a retirer aussi du fichier de l'administrateur quand il est
     * mis a jour vers {@code fromVersion() + 1}.
     *
     * @return chemins retires (avec leurs sous-cles), vide par defaut
     */
    default Set<String> removedKeys() {
        return Set.of();
    }
}
