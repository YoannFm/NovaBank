package dev.novadev.novabank.bukkit.diagnostic;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Garde les derniers avertissements et erreurs du journal de NovaBank pour le diagnostic (CDC 18). Se branche sur le
 * journal du plugin ; ne conserve que le niveau, la date et le message (jamais la trace de pile, qui peut citer des
 * valeurs de configuration).
 */
public final class RecentErrors extends Handler implements AutoCloseable {

    /** Nombre d'entrees conservees. */
    public static final int CAPACITY = 10;

    private final Logger logger;
    private final Deque<String> entries = new ArrayDeque<>();

    /**
     * Branche le collecteur sur le journal.
     *
     * @param logger journal du plugin
     */
    public RecentErrors(Logger logger) {
        this.logger = logger;
        setLevel(Level.WARNING);
        logger.addHandler(this);
    }

    @Override
    public void publish(LogRecord record) {
        if (record == null || record.getLevel().intValue() < Level.WARNING.intValue()) {
            return;
        }
        String message = record.getMessage() == null ? "" : record.getMessage();
        String line = Instant.ofEpochMilli(record.getMillis()) + " " + record.getLevel().getName() + " "
                + (message.length() > 160 ? message.substring(0, 160) + "..." : message);
        synchronized (entries) {
            entries.addLast(line);
            while (entries.size() > CAPACITY) {
                entries.removeFirst();
            }
        }
    }

    /**
     * @return les dernieres entrees, la plus ancienne en premier
     */
    public List<String> snapshot() {
        synchronized (entries) {
            return List.copyOf(entries);
        }
    }

    @Override
    public void flush() {
        // Rien a vider.
    }

    @Override
    public void close() {
        logger.removeHandler(this);
    }
}
