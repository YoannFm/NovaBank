/**
 * Coeur metier de NovaBank.
 *
 * <p>Logique metier pure (banques, stocks, valorisation, journal d'operations,
 * services de depot/retrait/reset) et ports vers l'exterieur (persistance,
 * synchronisation, inventaire, planification, horloge).
 *
 * <p>Aucune dependance Bukkit, Spigot, Paper ou BungeeCord n'est autorisee ici
 * (decision D3) : le module se teste en Java pur. La regle est verifiee par
 * {@code CoreArchitectureTest}.
 */
package dev.novadev.novabank.core;
