package dev.novadev.novabank.platform.folia;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/** Journal en memoire. */
final class RecordingLog {

    private final List<LogRecord> records = new CopyOnWriteArrayList<>();
    private final Logger logger = Logger.getAnonymousLogger();

    RecordingLog() {
        logger.setUseParentHandlers(false);
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

    Logger logger() {
        return logger;
    }

    List<String> messages(Level level) {
        return records.stream().filter(record -> record.getLevel() == level).map(LogRecord::getMessage).toList();
    }
}
