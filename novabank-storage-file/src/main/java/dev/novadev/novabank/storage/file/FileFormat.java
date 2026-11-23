package dev.novadev.novabank.storage.file;

/**
 * Format de l'instantane d'un stockage fichier.
 */
public enum FileFormat {

    /** Instantane JSON (Gson du serveur). */
    JSON,
    /** Instantane YAML (SnakeYAML du serveur). */
    YAML;

    /**
     * Cree le format a la demande : seule la bibliotheque du format choisi est chargee.
     *
     * @return le format de l'instantane
     */
    SnapshotCodec codec() {
        return this == JSON ? new JsonSnapshotCodec() : new YamlSnapshotCodec();
    }
}
