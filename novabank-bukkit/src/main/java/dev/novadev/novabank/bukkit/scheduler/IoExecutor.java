package dev.novadev.novabank.bukkit.scheduler;

import dev.novadev.novabank.core.text.ConsoleText;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Executeur dedie aux entrees/sorties du plugin (base de donnees, fichiers) : nombre de
 * threads fixe, file bornee, threads nommes {@code NovaBank-IO-n}. Ne depend d'aucune
 * API serveur : il sert tel quel sous Bukkit et sous Folia.
 *
 * <p>File pleine : la soumission est refusee ({@link RejectedExecutionException}) plutot
 * que d'accumuler sans limite. A la fermeture, les taches en file sont terminees dans la
 * limite du delai d'arret, puis les restantes sont abandonnees et signalees.
 */
public final class IoExecutor implements AutoCloseable {

    /** Prefixe des noms de threads. */
    public static final String THREAD_PREFIX = "NovaBank-IO-";

    private final ThreadPoolExecutor executor;
    private final int queueCapacity;
    private final Duration shutdownTimeout;
    private final Logger logger;

    /**
     * @param threads         nombre de threads (1 minimum)
     * @param queueCapacity   taches en attente au maximum (1 minimum)
     * @param shutdownTimeout attente maximale des taches a la fermeture
     * @param logger          journal des taches en echec et de l'arret
     */
    public IoExecutor(int threads, int queueCapacity, Duration shutdownTimeout, Logger logger) {
        if (threads < 1 || queueCapacity < 1) {
            throw new IllegalArgumentException(ConsoleText.tr("Threads et capacite doivent etre >= 1 : {0}, {1}",
                    threads, queueCapacity));
        }
        this.queueCapacity = queueCapacity;
        this.shutdownTimeout = Objects.requireNonNull(shutdownTimeout, "shutdownTimeout");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.executor = new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity), new NamedThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * Soumet une tache. Une exception levee par la tache est journalisee, jamais propagee
     * au thread de l'executeur.
     *
     * @param task tache
     * @throws RejectedExecutionException si la file est pleine ou l'executeur arrete
     */
    public void execute(Runnable task) {
        Objects.requireNonNull(task, "task");
        executor.execute(() -> {
            try {
                task.run();
            } catch (RuntimeException | LinkageError failure) {
                logger.log(Level.SEVERE, ConsoleText.tr("Tache asynchrone NovaBank en echec : {0}", failure), failure);
            }
        });
    }

    /**
     * @return l'etat courant
     */
    public IoStats stats() {
        return new IoStats(executor.getPoolSize(), executor.getMaximumPoolSize(), executor.getActiveCount(),
                executor.getQueue().size(), queueCapacity, executor.getCompletedTaskCount(), executor.isShutdown());
    }

    /**
     * Arrete l'executeur : plus aucune soumission, attente des taches dans la limite du
     * delai d'arret, puis interruption des restantes.
     */
    @Override
    public void close() {
        executor.shutdown();
        try {
            if (executor.awaitTermination(shutdownTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                return;
            }
            List<Runnable> dropped = executor.shutdownNow();
            logger.warning(
                    ConsoleText.tr("Executeur IO : delai d'arret de {0} s depasse, {1} tache(s) en attente abandonnee(s), taches en cours interrompues",
                            shutdownTimeout.toSeconds(), dropped.size()));
            if (!executor.awaitTermination(shutdownTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                logger.warning(ConsoleText.tr("Executeur IO : des threads {0}* ne se sont pas arretes", THREAD_PREFIX));
            }
        } catch (InterruptedException interrupted) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static final class NamedThreadFactory implements ThreadFactory {

        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, THREAD_PREFIX + counter.incrementAndGet());
            // Filet de securite : un thread oublie ne bloque jamais l'arret de la JVM.
            thread.setDaemon(true);
            return thread;
        }
    }
}
