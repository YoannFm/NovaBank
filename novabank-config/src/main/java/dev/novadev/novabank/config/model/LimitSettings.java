package dev.novadev.novabank.config.model;

/**
 * Section {@code limits} : garde-fous contre les abus.
 *
 * @param maxQuantityPerOperation quantite maximale deposee ou retiree en une operation
 * @param cooldownMs              delai minimal entre deux operations d'un meme joueur, en millisecondes
 */
public record LimitSettings(long maxQuantityPerOperation, long cooldownMs) {
}
