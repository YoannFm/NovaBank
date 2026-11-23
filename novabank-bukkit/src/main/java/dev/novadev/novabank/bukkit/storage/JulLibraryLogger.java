package dev.novadev.novabank.bukkit.storage;

import dev.novadev.novabank.libraries.LibraryLogger;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Branche le journal du telechargement et du chargement des pilotes sur le logger du plugin.
 */
public final class JulLibraryLogger implements LibraryLogger {

    private final Logger logger;

    /**
     * @param logger logger du plugin
     */
    public JulLibraryLogger(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public void info(String message) {
        logger.info(message);
    }

    @Override
    public void warning(String message) {
        logger.warning(message);
    }
}
