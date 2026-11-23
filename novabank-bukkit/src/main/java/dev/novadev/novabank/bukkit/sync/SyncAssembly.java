package dev.novadev.novabank.bukkit.sync;

import dev.novadev.novabank.bukkit.lifecycle.Lifecycle;
import dev.novadev.novabank.bukkit.lifecycle.StartupException;
import dev.novadev.novabank.config.model.RedisSettings;
import dev.novadev.novabank.core.application.TaskScheduler;
import dev.novadev.novabank.core.storage.StorageBackend;
import dev.novadev.novabank.core.sync.BankChangeFeed;
import dev.novadev.novabank.core.sync.RemoteChangeListener;
import dev.novadev.novabank.core.sync.SyncBus;
import dev.novadev.novabank.core.sync.SyncCoordinator;
import dev.novadev.novabank.core.sync.SyncTiming;
import dev.novadev.novabank.core.text.ConsoleText;
import dev.novadev.novabank.sync.redis.RedisConnectionSettings;
import dev.novadev.novabank.sync.redis.RedisSyncBusFactory;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Random;
import java.util.logging.Logger;

/**
 * Ouvre la synchronisation multi-serveur quand {@code redis.enabled} est vrai : bus Redis,
 * coordinateur et reconciliation periodique avec le stockage partage. Les deux sont enregistres
 * dans le cycle de vie (coordinateur ferme avant le bus).
 *
 * <p>Redis injoignable au demarrage n'empeche pas le plugin de demarrer : le bus se reconnecte et
 * la reconciliation par le stockage assure la coherence entre-temps.
 */
public final class SyncAssembly {

    private static final int PAGE_SIZE = SyncTiming.DEFAULT_PAGE_SIZE;

    private final Logger logger;
    private final Clock clock;

    /**
     * @param logger journal du plugin
     * @param clock  horloge
     */
    public SyncAssembly(Logger logger, Clock clock) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * @param settings  section redis (active)
     * @param storage   stockage partage
     * @param scheduler planificateur dont l'executeur asynchrone lit le stockage
     * @param lifecycle cycle de vie qui recoit le bus et le coordinateur
     * @param listener  receveur des changements faits par les autres serveurs
     * @return le coordinateur demarre
     * @throws StartupException si le stockage ne peut pas fournir de flux de changements
     */
    public SyncCoordinator open(RedisSettings settings, StorageBackend storage, TaskScheduler scheduler,
                                Lifecycle lifecycle, RemoteChangeListener listener) {
        BankChangeFeed feed = storage.changeFeed().orElseThrow(() -> new StartupException(
                ConsoleText.tr("redis.enabled : le stockage actif ne fournit pas de flux de changements et ne peut pas etre partage entre serveurs (utilisez MYSQL, MARIADB ou MONGODB)")));
        String serverId = settings.serverName() + "-" + HexFormat.of().formatHex(randomBytes());
        RedisConnectionSettings connection = new RedisConnectionSettings(settings.host(), settings.port(),
                settings.password().reveal(), settings.ssl(), settings.channelPrefix(), settings.connectTimeoutMs(),
                settings.socketTimeoutMs());
        SyncBus bus = lifecycle.register("bus Redis", new RedisSyncBusFactory(connection, logger).open());
        SyncTiming timing = new SyncTiming(Duration.ofSeconds(settings.pollIntervalSeconds()),
                Duration.ofSeconds(settings.overlapSeconds()), PAGE_SIZE);
        SyncCoordinator coordinator = lifecycle.register("coordination multi-serveur", new SyncCoordinator(serverId,
                feed, bus, change -> {
                    logger.fine(ConsoleText.tr("Banque {0} modifiee ailleurs (version {1})",
                            change.bankId(), change.version()));
                    listener.onRemoteChange(change);
                }, clock, timing, logger));
        coordinator.start(scheduler);
        logger.info(
                ConsoleText.tr("Synchronisation multi-serveur active : serveur {0}, Redis {1}, reconciliation toutes les {2} s",
                        serverId, connection.describe(), settings.pollIntervalSeconds()));
        return coordinator;
    }

    private static byte[] randomBytes() {
        byte[] bytes = new byte[4];
        new Random().nextBytes(bytes);
        return bytes;
    }
}
