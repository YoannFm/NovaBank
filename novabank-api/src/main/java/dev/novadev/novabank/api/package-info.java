/**
 * API publique de NovaBank.
 *
 * <p>Ce module contient uniquement les contrats publics stables destines aux
 * plugins tiers et aux modules d'integration : interfaces, types de valeur et
 * evenements Bukkit. Il n'est jamais obfusque et sa compatibilite binaire est
 * preservee entre versions mineures.
 *
 * <p>Il depend de {@code spigot-api} en scope {@code provided} : l'API serveur
 * est fournie a l'execution et n'est pas transmise aux modules dependants.
 *
 * <p>Ce package racine contient les identifiants transverses
 * ({@link dev.novadev.novabank.api.BankId}, {@link dev.novadev.novabank.api.OperationId}).
 */
package dev.novadev.novabank.api;
