/**
 * Implementations Bukkit du port {@link dev.novadev.novabank.core.application.TaskScheduler} :
 * base commune {@link dev.novadev.novabank.bukkit.scheduler.PluginScheduler} (executeur
 * d'entrees/sorties dedie, taches periodiques, phase d'arret), planificateur Bukkit/Paper et
 * choix du planificateur selon la plateforme (l'implementation Folia est dans le module
 * {@code novabank-platform-folia}, chargee par son nom).
 */
package dev.novadev.novabank.bukkit.scheduler;
