package dev.novadev.novabank.bukkit.lifecycle;

import java.util.List;
import java.util.Objects;

/**
 * Threads encore vivants dont le nom commence par un prefixe (verification de fin d'arret).
 */
public final class RemainingThreads {

    /** Prefixe commun des threads du plugin (executeur IO, pool HikariCP). */
    public static final String NOVABANK_PREFIX = "NovaBank";

    private RemainingThreads() {
    }

    /**
     * @param prefix prefixe des noms
     * @return noms des threads vivants concernes, hors thread courant, tries
     */
    public static List<String> named(String prefix) {
        Objects.requireNonNull(prefix, "prefix");
        Thread current = Thread.currentThread();
        return Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> thread != current && thread.isAlive() && thread.getName().startsWith(prefix))
                .map(Thread::getName)
                .sorted()
                .toList();
    }
}
