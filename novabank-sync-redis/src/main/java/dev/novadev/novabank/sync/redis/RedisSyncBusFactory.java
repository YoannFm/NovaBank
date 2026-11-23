package dev.novadev.novabank.sync.redis;

import dev.novadev.novabank.core.sync.SyncBus;
import dev.novadev.novabank.core.sync.SyncBusFactory;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Fabrique du bus Redis.
 */
public final class RedisSyncBusFactory implements SyncBusFactory {

    private final RedisConnectionSettings settings;
    private final Logger logger;

    /**
     * @param settings reglages de connexion
     * @param logger   journal du plugin
     */
    public RedisSyncBusFactory(RedisConnectionSettings settings, Logger logger) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public SyncBus open() {
        return new RedisSyncBus(settings, logger);
    }
}
