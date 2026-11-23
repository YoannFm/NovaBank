package dev.novadev.novabank.core.testing;

import dev.novadev.novabank.core.application.ScheduledTask;
import dev.novadev.novabank.core.application.TaskScheduler;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;

/**
 * Planificateur de test deterministe : les taches sont mises en file puis executees par
 * {@link #runAll()} sur le thread du test, dans l'ordre de soumission. Le contexte de la
 * tache en cours ({@link #context()}) permet aux doublures de verifier qu'elles sont
 * appelees sur le bon "thread".
 */
public final class ManualScheduler implements TaskScheduler {

    /** Contexte d'execution simule. */
    public enum Context {
        /** Hors de toute tache planifiee (code du test). */
        NONE,
        /** Contexte global. */
        GLOBAL,
        /** Thread du joueur. */
        PLAYER,
        /** Executeur d'entrees/sorties. */
        IO
    }

    private static final ThreadLocal<Context> CURRENT = ThreadLocal.withInitial(() -> Context.NONE);

    private final Deque<Runnable> queue = new ArrayDeque<>();
    private final Set<UUID> online = ConcurrentHashMap.newKeySet();
    private boolean rejectIo;

    /**
     * @return le contexte de la tache en cours sur ce thread
     */
    public static Context context() {
        return CURRENT.get();
    }

    /**
     * @param playerId joueur a connecter
     * @return ce planificateur
     */
    public ManualScheduler connect(UUID playerId) {
        online.add(playerId);
        return this;
    }

    /**
     * @param playerId joueur a deconnecter
     */
    public void disconnect(UUID playerId) {
        online.remove(playerId);
    }

    /**
     * @param reject refuser les taches IO (executeur sature)
     */
    public void rejectIo(boolean reject) {
        this.rejectIo = reject;
    }

    /**
     * Execute les taches en file, y compris celles soumises pendant l'execution.
     *
     * @return nombre de taches executees
     */
    public int runAll() {
        int count = 0;
        while (!queue.isEmpty()) {
            queue.poll().run();
            count++;
        }
        return count;
    }

    /**
     * Execute au plus une tache.
     *
     * @return {@code true} si une tache a ete executee
     */
    public boolean runOne() {
        Runnable next = queue.poll();
        if (next == null) {
            return false;
        }
        next.run();
        return true;
    }

    @Override
    public void runGlobal(Runnable task) {
        queue.add(in(Context.GLOBAL, task));
    }

    @Override
    public void runForPlayer(UUID playerId, Runnable task, Runnable ifOffline) {
        queue.add(() -> {
            if (online.contains(playerId)) {
                in(Context.PLAYER, task).run();
            } else {
                in(Context.GLOBAL, ifOffline).run();
            }
        });
    }

    @Override
    public void runAsync(Runnable task) {
        if (rejectIo) {
            throw new RejectedExecutionException("executeur IO de test sature");
        }
        queue.add(in(Context.IO, task));
    }

    @Override
    public ScheduledTask runLaterGlobal(Runnable task, Duration delay) {
        runGlobal(task);
        return NeverCancelled.INSTANCE;
    }

    @Override
    public ScheduledTask runRepeatingAsync(Runnable task, Duration initialDelay, Duration period) {
        // Jamais declenchee seule : le test appelle lui-meme la tache qu'il veut observer.
        return NeverCancelled.INSTANCE;
    }

    private static Runnable in(Context context, Runnable task) {
        return () -> {
            Context previous = CURRENT.get();
            CURRENT.set(context);
            try {
                task.run();
            } finally {
                CURRENT.set(previous);
            }
        };
    }

    private enum NeverCancelled implements ScheduledTask {
        INSTANCE;

        @Override
        public void cancel() {
            // Rien a annuler : la tache est deja en file.
        }

        @Override
        public boolean isCancelled() {
            return false;
        }
    }
}
