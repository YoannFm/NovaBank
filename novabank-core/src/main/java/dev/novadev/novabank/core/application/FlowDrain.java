package dev.novadev.novabank.core.application;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Suivi des flux qui deplacent des items (depot, retrait, remise des livraisons) et
 * phase d'arret ("drain") qui les termine avant la fermeture de l'executeur IO.
 *
 * <p>Pendant l'arret :
 * <ul>
 *   <li>aucun nouveau flux n'est accepte ({@link #tryStart()} renvoie {@code false}) ;</li>
 *   <li>chaque etape "thread du joueur" d'un flux en cours prend sa branche "joueur
 *       parti" ({@link Flow}) : rien n'est donne ni retire de l'inventaire, les items a
 *       rendre d'un depot refuse deviennent des livraisons en attente, un retrait reserve
 *       est solde avec 0 remis selon la politique D13 (livraison en attente ou retour en
 *       banque) pendant que la base est encore ouverte ;</li>
 *   <li>la plateforme attend la fin des flux ({@link #awaitIdle}) en executant elle-meme
 *       les taches du contexte global/joueur qu'elle ne peut plus planifier (sous Bukkit,
 *       le planificateur n'execute plus rien une fois le plugin desactive ; sous Folia, les
 *       planificateurs des regions sont deja arretes quand le plugin est desactive).</li>
 * </ul>
 *
 * <p>Maintenance (migration du stockage) : {@link #pause()} refuse les nouveaux flux sans
 * changer le deroulement de ceux en cours, {@link #awaitIdle(Duration)} attend qu'ils soient
 * termines, {@link #resume()} les autorise de nouveau.
 *
 * <p>Sur pour plusieurs threads.
 */
public final class FlowDrain {

    private static final Duration PUMP_SLICE = Duration.ofMillis(50);

    private final AtomicInteger active = new AtomicInteger();
    private final AtomicBoolean shuttingDown = new AtomicBoolean();
    private final AtomicBoolean paused = new AtomicBoolean();

    /**
     * Execute les taches du contexte global/joueur en attente pendant l'arret.
     */
    @FunctionalInterface
    public interface Pump {

        /**
         * Execute les taches en attente, en attendant au plus {@code maxWait} qu'une
         * tache arrive s'il n'y en a aucune.
         *
         * @param maxWait attente maximale
         * @throws InterruptedException si le thread est interrompu pendant l'attente
         */
        void runPending(Duration maxWait) throws InterruptedException;
    }

    /**
     * Enregistre le debut d'un flux.
     *
     * @return {@code true} si le flux peut commencer ; {@code false} pendant l'arret ou la
     *         maintenance (rien n'est alors enregistre)
     */
    public boolean tryStart() {
        active.incrementAndGet();
        if (shuttingDown.get() || paused.get()) {
            active.decrementAndGet();
            return false;
        }
        return true;
    }

    /**
     * Enregistre la fin d'un flux commence par {@link #tryStart()}.
     */
    public void finished() {
        active.decrementAndGet();
    }

    /**
     * @return {@code true} des le debut de l'arret
     */
    public boolean isShuttingDown() {
        return shuttingDown.get();
    }

    /**
     * @return {@code true} pendant la maintenance
     */
    public boolean isPaused() {
        return paused.get();
    }

    /**
     * Commence la maintenance : les nouveaux flux sont refuses, ceux en cours continuent
     * normalement.
     *
     * @return {@code false} si la maintenance etait deja en cours
     */
    public boolean pause() {
        return paused.compareAndSet(false, true);
    }

    /**
     * Termine la maintenance.
     */
    public void resume() {
        paused.set(false);
    }

    /**
     * Attend la fin des flux en cours (maintenance), sans executer de tache : le serveur
     * continue de planifier normalement les etapes des flux.
     *
     * @param timeout attente maximale
     * @return {@code true} si plus aucun flux n'est en cours
     * @throws InterruptedException si l'attente est interrompue
     */
    public boolean awaitIdle(Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + Objects.requireNonNull(timeout, "timeout").toNanos();
        while (activeFlows() > 0) {
            if (System.nanoTime() - deadline >= 0) {
                return false;
            }
            Thread.sleep(PUMP_SLICE.toMillis());
        }
        return true;
    }

    /**
     * @return nombre de flux en cours
     */
    public int activeFlows() {
        return Math.max(0, active.get());
    }

    /**
     * Commence l'arret : refuse les nouveaux flux, puis attend la fin de ceux en cours en
     * executant les taches pompees, dans la limite du delai.
     *
     * @param timeout delai maximal, positif ou nul
     * @param pump    execution des taches du contexte global/joueur en attente
     * @return {@code true} si tous les flux sont termines, {@code false} si le delai est
     *         depasse ou l'attente interrompue
     */
    public boolean drain(Duration timeout, Pump pump) {
        Objects.requireNonNull(timeout, "timeout");
        Objects.requireNonNull(pump, "pump");
        shuttingDown.set(true);
        long deadline = System.nanoTime() + timeout.toNanos();
        try {
            while (activeFlows() > 0) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    return false;
                }
                pump.runPending(Duration.ofNanos(Math.min(remaining, PUMP_SLICE.toNanos())));
            }
            pump.runPending(Duration.ZERO);
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
