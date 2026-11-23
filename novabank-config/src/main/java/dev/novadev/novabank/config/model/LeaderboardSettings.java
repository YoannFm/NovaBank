package dev.novadev.novabank.config.model;

/**
 * Classement d'un type d'entite (section {@code leaderboards.<type>}).
 *
 * @param enabled        classement actif
 * @param size           nombre d'entrees affichees
 * @param refreshSeconds intervalle de recalcul, en secondes
 * @param perCategory    classements supplementaires par categorie d'item
 */
public record LeaderboardSettings(boolean enabled, int size, int refreshSeconds, boolean perCategory) {
}
