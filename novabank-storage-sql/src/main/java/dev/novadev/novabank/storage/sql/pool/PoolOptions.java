package dev.novadev.novabank.storage.sql.pool;

import dev.novadev.novabank.core.text.ConsoleText;

/**
 * Dimensionnement d'un pool de connexions vers un serveur SQL.
 *
 * @param maxSize             nombre maximal de connexions (au moins 1)
 * @param minIdle             connexions inactives conservees (0 a {@code maxSize})
 * @param connectionTimeoutMs attente maximale d'une connexion, en millisecondes (250 minimum)
 * @param maxLifetimeMs       duree de vie maximale d'une connexion, en millisecondes (30 000 minimum)
 */
public record PoolOptions(int maxSize, int minIdle, long connectionTimeoutMs, long maxLifetimeMs) {

    /**
     * @throws IllegalArgumentException si une valeur sort des bornes acceptees par HikariCP
     */
    public PoolOptions {
        if (maxSize < 1 || minIdle < 0 || minIdle > maxSize) {
            throw new IllegalArgumentException(ConsoleText.tr("Taille de pool invalide : max {0}, min {1}",
                    maxSize, minIdle));
        }
        if (connectionTimeoutMs < 250 || maxLifetimeMs < 30_000) {
            throw new IllegalArgumentException(
                    ConsoleText.tr("Delais de pool invalides : connexion {0} ms, duree de vie {1} ms",
                            connectionTimeoutMs, maxLifetimeMs));
        }
    }
}
