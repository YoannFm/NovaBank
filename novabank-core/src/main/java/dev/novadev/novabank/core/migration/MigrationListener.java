package dev.novadev.novabank.core.migration;

/**
 * Suivi de la progression d'une migration (journal du serveur). Appele sur le thread de la
 * migration.
 */
@FunctionalInterface
public interface MigrationListener {

    /**
     * @param message etape ou progression, en francais
     */
    void progress(String message);
}
