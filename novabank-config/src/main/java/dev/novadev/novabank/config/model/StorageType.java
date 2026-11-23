package dev.novadev.novabank.config.model;

/**
 * Backend de stockage des banques.
 */
public enum StorageType {

    /** Fichiers YAML, mono-serveur. */
    YAML(true, false),
    /** Fichiers JSON, mono-serveur. */
    JSON(true, false),
    /** Base SQLite locale, mono-serveur. */
    SQLITE(true, false),
    /** Serveur MySQL (pilote MariaDB telecharge, D6). */
    MYSQL(false, true),
    /** Serveur MariaDB. */
    MARIADB(false, true),
    /** Serveur MongoDB. */
    MONGODB(false, false);

    private final boolean singleServer;
    private final boolean sqlServer;

    StorageType(boolean singleServer, boolean sqlServer) {
        this.singleServer = singleServer;
        this.sqlServer = sqlServer;
    }

    /**
     * Indique si ce stockage ne peut servir qu'un seul serveur (D4) : il est alors
     * incompatible avec la synchronisation Redis.
     *
     * @return {@code true} pour YAML, JSON et SQLite
     */
    public boolean singleServer() {
        return singleServer;
    }

    /**
     * Indique si ce stockage utilise la section {@code storage.sql}.
     *
     * @return {@code true} pour MySQL et MariaDB
     */
    public boolean sqlServer() {
        return sqlServer;
    }
}
