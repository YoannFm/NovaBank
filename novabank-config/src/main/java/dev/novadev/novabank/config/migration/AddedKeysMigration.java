package dev.novadev.novabank.config.migration;

import java.util.Map;

/**
 * Etape de version qui n'ajoute que des cles (toutes avec une valeur par defaut) : l'arbre est
 * inchange. Les cles nouvelles sont ecrites dans le fichier de l'administrateur par la plateforme
 * (fusion avec le fichier fourni) ; tant qu'elles en sont absentes, leur valeur par defaut
 * s'applique.
 *
 * <p>Version 1 vers 2 : cles apparues pendant le developpement de la version 1 (GUI, commandes,
 * securite, MongoDB, stockage fichier), absentes des fichiers copies par les premieres versions.
 */
final class AddedKeysMigration implements ConfigMigration {

    private final int fromVersion;

    /**
     * @param fromVersion version de depart
     */
    AddedKeysMigration(int fromVersion) {
        this.fromVersion = fromVersion;
    }

    @Override
    public int fromVersion() {
        return fromVersion;
    }

    @Override
    public Map<String, Object> migrate(Map<String, Object> tree) {
        return tree;
    }
}
