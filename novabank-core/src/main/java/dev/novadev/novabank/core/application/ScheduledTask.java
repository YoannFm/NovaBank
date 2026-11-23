package dev.novadev.novabank.core.application;

/**
 * Tache differee ou periodique planifiee par un {@link TaskScheduler}.
 */
public interface ScheduledTask {

    /**
     * Annule la tache. Sans effet si elle est deja annulee ou terminee ; une execution
     * deja commencee n'est pas interrompue.
     */
    void cancel();

    /**
     * Indique si la tache a ete annulee.
     *
     * @return {@code true} apres {@link #cancel()}
     */
    boolean isCancelled();
}
