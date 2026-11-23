/**
 * Ports de la couche application, implementes par la plateforme (Bukkit, Folia...).
 *
 * <p>Aucune dependance serveur : les services du coeur planifient leurs traitements
 * a travers {@link dev.novadev.novabank.core.application.TaskScheduler} sans savoir
 * s'ils tournent sur le thread principal de Bukkit ou sur les regions de Folia.
 */
package dev.novadev.novabank.core.application;
