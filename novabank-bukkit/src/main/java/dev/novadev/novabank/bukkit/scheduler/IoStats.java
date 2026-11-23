package dev.novadev.novabank.bukkit.scheduler;

/**
 * Etat instantane de l'executeur d'entrees/sorties.
 *
 * @param threads       threads vivants
 * @param maxThreads    threads au maximum
 * @param active        taches en cours
 * @param queued        taches en attente
 * @param queueCapacity capacite de la file
 * @param completed     taches terminees depuis le demarrage
 * @param shutdown      {@code true} si l'arret est demande
 */
public record IoStats(int threads, int maxThreads, int active, int queued, int queueCapacity, long completed,
                      boolean shutdown) {
}
