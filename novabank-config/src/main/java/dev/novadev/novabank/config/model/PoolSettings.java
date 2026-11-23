package dev.novadev.novabank.config.model;

/**
 * Section {@code storage.sql.pool} : pool de connexions SQL.
 *
 * @param maxSize             nombre maximal de connexions
 * @param minIdle             nombre minimal de connexions inactives conservees
 * @param connectionTimeoutMs attente maximale d'une connexion, en millisecondes
 * @param maxLifetimeMs       duree de vie maximale d'une connexion, en millisecondes
 */
public record PoolSettings(int maxSize, int minIdle, long connectionTimeoutMs, long maxLifetimeMs) {
}
