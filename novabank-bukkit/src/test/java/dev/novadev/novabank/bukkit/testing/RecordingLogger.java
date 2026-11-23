package dev.novadev.novabank.bukkit.testing;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Logger isole qui conserve ses enregistrements pour les assertions.
 */
public final class RecordingLogger {

    private final List<LogRecord> records = new CopyOnWriteArrayList<>();
    private final Logger logger = Logger.getAnonymousLogger();

    /**
     * Cree le logger, sans sortie console.
     */
    public RecordingLogger() {
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.ALL);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {
                // Rien a vider.
            }

            @Override
            public void close() {
                // Rien a fermer.
            }
        });
    }

    /**
     * @return le logger
     */
    public Logger logger() {
        return logger;
    }

    /**
     * @return messages journalises, dans l'ordre
     */
    public List<String> messages() {
        return records.stream().map(LogRecord::getMessage).toList();
    }

    /**
     * @param level niveau
     * @return messages de ce niveau
     */
    public List<String> messages(Level level) {
        return records.stream().filter(record -> record.getLevel().equals(level)).map(LogRecord::getMessage).toList();
    }

    /**
     * @return enregistrements portant une exception
     */
    public List<LogRecord> withThrowable() {
        return records.stream().filter(record -> record.getThrown() != null).toList();
    }
}
