package dev.novadev.novabank.core.testing;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Lance la meme tache sur N threads reels, liberes ensemble par un
 * {@link CountDownLatch} pour maximiser les collisions.
 */
public final class ConcurrentRunner {

    private static final long TIMEOUT_SECONDS = 60L;

    private ConcurrentRunner() {
    }

    /**
     * Tache executee par chaque thread.
     */
    @FunctionalInterface
    public interface ThreadTask {

        /**
         * @param threadIndex indice du thread, de 0 a N-1
         * @throws Exception toute erreur, remontee a l'appelant
         */
        void run(int threadIndex) throws Exception;
    }

    /**
     * Execute {@code task} sur {@code threads} threads et attend qu'ils aient tous fini.
     *
     * @param threads nombre de threads
     * @param task    tache a executer
     * @throws Exception la premiere erreur d'un thread, ou un depassement de delai
     */
    public static void run(int threads, ThreadTask task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int index = 0; index < threads; index++) {
                int threadIndex = index;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    task.run(threadIndex);
                    return null;
                }));
            }
            if (!ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new TimeoutException("Threads non demarres");
            }
            start.countDown();
            for (Future<?> future : futures) {
                try {
                    future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                } catch (ExecutionException failure) {
                    throw failure.getCause() instanceof Exception cause ? cause : failure;
                }
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
