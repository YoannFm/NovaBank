package dev.novadev.novabank.bukkit.scheduler;

import dev.novadev.novabank.core.text.ConsoleText;
import java.time.Duration;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Taches confiees au planificateur du serveur que le plugin doit pouvoir terminer lui-meme
 * pendant son arret, quand le serveur n'execute plus ses taches (Bukkit : plugin desactive ;
 * Folia : planificateurs des regions deja arretes). Sans dependance serveur. Sur pour
 * plusieurs threads ; {@link #runPending} est appele par un seul thread. Pilote uniquement
 * par {@link PluginScheduler} : une plateforme ne voit que {@link Tracked}.
 *
 * <ul>
 *   <li>en marche : chaque tache confiee au serveur est suivie ({@link #track}) jusqu'a son
 *       execution ;</li>
 *   <li>plugin desactive ou arret commence : les nouvelles taches sont mises en file
 *       ({@link #offer}) ; au debut de l'arret ({@link #startDraining()}), les taches
 *       suivies pas encore executees rejoignent la file sous leur forme "arret" ;</li>
 *   <li>le thread qui desactive le plugin execute la file ({@link #runPending}) ; chaque
 *       tache n'est executee qu'une fois, meme si le serveur la lance aussi ;</li>
 *   <li>ferme ({@link #close()}) : plus rien n'est accepte.</li>
 * </ul>
 */
public final class ShutdownTasks {

    /** Ordre de soumission conserve : la pompe reprend les taches dans l'ordre ou elles ont ete confiees. */
    private final Set<Tracked> tracked = Collections.synchronizedSet(new LinkedHashSet<>());
    private final BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
    private final AtomicBoolean draining = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Logger logger;

    /**
     * @param logger journal des taches en echec pendant l'arret
     */
    ShutdownTasks(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * @param task       tache a confier au serveur
     * @param whenDrained forme executee par la pompe d'arret si le serveur ne l'a pas lancee
     * @return la tache suivie : executee au plus une fois (serveur, repli ou pompe d'arret)
     */
    Tracked track(Runnable task, Runnable whenDrained) {
        Tracked once = new Tracked(Objects.requireNonNull(task, "task"),
                Objects.requireNonNull(whenDrained, "whenDrained"));
        tracked.add(once);
        if (draining.get()) {
            queue.add(new Drained(once));
        }
        return once;
    }

    /**
     * @return {@code true} des le debut de l'arret
     */
    boolean isDraining() {
        return draining.get();
    }

    /**
     * Met une tache en file, a executer par {@link #runPending}.
     *
     * @param task tache
     * @return {@code false} si la file est fermee (tache ignoree)
     */
    boolean offer(Runnable task) {
        Objects.requireNonNull(task, "task");
        if (closed.get()) {
            return false;
        }
        queue.add(task);
        return true;
    }

    /**
     * Debut de l'arret : les taches suivies non executees rejoignent la file.
     */
    void startDraining() {
        draining.set(true);
        synchronized (tracked) {
            for (Tracked task : tracked) {
                queue.add(new Drained(task));
            }
        }
    }

    /**
     * Execute les taches en file sur le thread appelant, en attendant au plus
     * {@code maxWait} la premiere si la file est vide.
     *
     * @param maxWait attente maximale
     * @throws InterruptedException si l'attente est interrompue
     */
    void runPending(Duration maxWait) throws InterruptedException {
        Runnable next = maxWait.isZero() ? queue.poll() : queue.poll(maxWait.toNanos(), TimeUnit.NANOSECONDS);
        while (next != null) {
            try {
                next.run();
            } catch (RuntimeException failure) {
                logger.log(Level.SEVERE, ConsoleText.tr("Tache NovaBank en echec pendant l'arret : {0}",
                        failure), failure);
            }
            next = queue.poll();
        }
    }

    /**
     * Ferme la file : plus aucune tache acceptee.
     *
     * @return nombre de taches restees en file, jamais executees
     */
    int close() {
        closed.set(true);
        Set<Runnable> abandoned = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Runnable task = queue.poll(); task != null; task = queue.poll()) {
            abandoned.add(task instanceof Drained drained ? drained.task() : task);
        }
        synchronized (tracked) {
            abandoned.addAll(tracked);
            tracked.clear();
        }
        abandoned.removeIf(task -> task instanceof Tracked once && !once.claimable());
        return abandoned.size();
    }

    /** Forme "arret" d'une tache suivie, mise en file. */
    private record Drained(Tracked task) implements Runnable {

        @Override
        public void run() {
            task.runDrained();
        }
    }

    /**
     * Tache suivie, executee au plus une fois sous l'une de ses formes.
     */
    public final class Tracked implements Runnable {

        private final Runnable task;
        private final Runnable whenDrained;
        private final AtomicBoolean claimed = new AtomicBoolean();

        private Tracked(Runnable task, Runnable whenDrained) {
            this.task = task;
            this.whenDrained = whenDrained;
        }

        boolean claimable() {
            return !claimed.get();
        }

        /**
         * Execution par le serveur.
         */
        @Override
        public void run() {
            runInstead(task);
        }

        /**
         * Execute une autre forme a la place (repli decide par la plateforme, par exemple
         * "joueur parti"), si aucune forme n'a encore ete executee.
         *
         * @param other forme executee
         */
        public void runInstead(Runnable other) {
            Objects.requireNonNull(other, "other");
            if (claimed.compareAndSet(false, true)) {
                tracked.remove(this);
                other.run();
            }
        }

        private void runDrained() {
            runInstead(whenDrained);
        }
    }
}
