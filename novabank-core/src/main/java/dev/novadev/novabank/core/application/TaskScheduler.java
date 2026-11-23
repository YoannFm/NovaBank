package dev.novadev.novabank.core.application;

import java.time.Duration;
import java.util.UUID;

/**
 * Port de planification des traitements, independant de la plateforme.
 *
 * <p>Trois contextes d'execution :
 * <ul>
 *   <li>global : etat du serveur non rattache a un joueur (thread principal sous Bukkit,
 *       planificateur global sous Folia) ;</li>
 *   <li>joueur : manipulation de l'inventaire ou de l'entite d'un joueur (thread principal
 *       sous Bukkit, planificateur de l'entite sous Folia, donc thread de sa region) ;</li>
 *   <li>asynchrone : entrees/sorties (base de donnees, fichiers, reseau) sur un executeur
 *       dedie et borne appartenant au plugin, jamais sur un thread du serveur.</li>
 * </ul>
 *
 * <p>Les delais sont exprimes en {@link Duration} ; l'implementation les convertit dans
 * son unite (ticks de 50 ms sous Bukkit). Une tache soumise apres l'arret du plugin est
 * ignoree (et signalee) par l'implementation.
 */
public interface TaskScheduler {

    /**
     * Execute une tache dans le contexte global, au prochain tick.
     *
     * @param task tache
     */
    void runGlobal(Runnable task);

    /**
     * Execute une tache dans le contexte d'un joueur connecte.
     *
     * @param playerId  joueur
     * @param task      tache executee si le joueur est encore connecte au moment de l'execution
     * @param ifOffline tache executee a la place si le joueur est parti (par exemple pour
     *                  mettre des items en livraison differee). Elle ne doit toucher ni au
     *                  joueur ni au monde : selon la plateforme, elle s'execute dans le
     *                  contexte global, sur le thread qui retire l'entite du joueur (Folia)
     *                  ou sur le thread qui desactive le plugin (phase d'arret)
     */
    void runForPlayer(UUID playerId, Runnable task, Runnable ifOffline);

    /**
     * Execute une tache d'entrees/sorties sur l'executeur asynchrone du plugin.
     *
     * @param task tache
     * @throws java.util.concurrent.RejectedExecutionException si la file de l'executeur est
     *                                                         pleine ou l'executeur arrete
     */
    void runAsync(Runnable task);

    /**
     * Execute une tache dans le contexte global apres un delai.
     *
     * @param task  tache
     * @param delay delai, positif ou nul
     * @return la tache planifiee, annulable
     */
    ScheduledTask runLaterGlobal(Runnable task, Duration delay);

    /**
     * Execute periodiquement une tache sur l'executeur asynchrone. Une execution n'est
     * jamais lancee tant que la precedente n'est pas terminee (pas d'empilement si la
     * tache est plus longue que la periode).
     *
     * @param task         tache
     * @param initialDelay delai avant la premiere execution, positif ou nul
     * @param period       periode, strictement positive
     * @return la tache planifiee, annulable
     */
    ScheduledTask runRepeatingAsync(Runnable task, Duration initialDelay, Duration period);
}
