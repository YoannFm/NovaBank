package dev.novadev.novabank.libraries.testing;

import dev.novadev.novabank.libraries.LibraryLogger;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Journal qui conserve les messages, prefixes par leur niveau.
 */
public final class RecordingLogger implements LibraryLogger {

    private final List<String> lines = new CopyOnWriteArrayList<>();

    @Override
    public void info(String message) {
        lines.add("INFO " + message);
    }

    @Override
    public void warning(String message) {
        lines.add("WARN " + message);
    }

    public List<String> lines() {
        return List.copyOf(lines);
    }
}
