package dev.novadev.novabank.sync.redis;

import dev.novadev.novabank.core.sync.SyncBus;
import dev.novadev.novabank.core.sync.SyncBusStatus;
import dev.novadev.novabank.core.sync.SyncEvent;
import dev.novadev.novabank.core.text.ConsoleText;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link SyncBus} sur Redis Pub/Sub.
 *
 * <p>Deux threads daemon, chacun avec sa connexion : l'abonne (SUBSCRIBE, PING de controle quand
 * le canal est silencieux, reconnexion avec attente croissante de 1 a 10 s) et l'editeur (file
 * bornee d'evenements, reconnexion a la demande). Aucun appel n'attend Redis depuis le thread de
 * l'appelant, et aucun n'echoue : un Redis coupe se traduit par {@code publish} = {@code false} et
 * {@link #status()} non connecte. Les messages perdus sont rattrapes par la lecture du stockage.
 */
public final class RedisSyncBus implements SyncBus {

    private static final int QUEUE_CAPACITY = 4096;
    private static final long MIN_BACKOFF_MS = 1_000;
    private static final long MAX_BACKOFF_MS = 10_000;
    private static final int HEARTBEAT_MS = 15_000;

    private final RedisConnectionSettings settings;
    private final Logger logger;
    private final int heartbeatMs;
    private final BlockingQueue<String[]> outbox = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    private final Thread subscriberThread;
    private final Thread publisherThread;

    private volatile Consumer<SyncEvent> listener;
    private volatile BiConsumer<String, String> controlListener;
    private volatile boolean closed;
    private volatile boolean subscribed;
    private volatile boolean backingOff;
    private volatile String lastError = "";
    private volatile RespConnection subscriberConnection;
    private volatile RespConnection publisherConnection;

    /**
     * Demarre le bus (les connexions s'etablissent en arriere-plan).
     *
     * @param settings reglages de connexion
     * @param logger   journal du plugin
     */
    public RedisSyncBus(RedisConnectionSettings settings, Logger logger) {
        this(settings, logger, HEARTBEAT_MS);
    }

    RedisSyncBus(RedisConnectionSettings settings, Logger logger, int heartbeatMs) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.heartbeatMs = heartbeatMs;
        this.subscriberThread = new Thread(this::subscribeLoop, "NovaBank-Redis-Subscriber");
        this.publisherThread = new Thread(this::publishLoop, "NovaBank-Redis-Publisher");
        subscriberThread.setDaemon(true);
        publisherThread.setDaemon(true);
        subscriberThread.start();
        publisherThread.start();
    }

    @Override
    public boolean publish(SyncEvent event) {
        if (closed || backingOff) {
            return false;
        }
        try {
            return outbox.offer(new String[] {settings.channel(), SyncPayloads.encode(event)});
        } catch (IllegalArgumentException invalid) {
            lastError = invalid.getMessage();
            return false;
        }
    }

    @Override
    public boolean publishControl(String origin, String command) {
        if (closed || backingOff) {
            return false;
        }
        try {
            return outbox.offer(new String[] {settings.controlChannel(), SyncPayloads.encodeControl(origin, command)});
        } catch (IllegalArgumentException invalid) {
            lastError = invalid.getMessage();
            return false;
        }
    }

    @Override
    public void subscribeControl(BiConsumer<String, String> receiver) {
        this.controlListener = Objects.requireNonNull(receiver, "receiver");
    }

    @Override
    public void subscribe(Consumer<SyncEvent> receiver) {
        this.listener = Objects.requireNonNull(receiver, "receiver");
    }

    @Override
    public SyncBusStatus status() {
        // "Connecte" = liaison d'abonnement etablie (sondee par PING) : l'editeur, lui, ne se rouvre qu'a
        // l'envoi suivant, son dernier echec reste visible dans lastError.
        return new SyncBusStatus(subscribed && !closed, settings.describe(), lastError);
    }

    @Override
    public void close() {
        closed = true;
        outbox.clear();
        closeQuietly(subscriberConnection);
        closeQuietly(publisherConnection);
        subscriberThread.interrupt();
        publisherThread.interrupt();
        join(subscriberThread);
        join(publisherThread);
        subscribed = false;
    }

    private void subscribeLoop() {
        long backoff = MIN_BACKOFF_MS;
        while (!closed) {
            try (RespConnection connection = RespConnection.open(settings, heartbeatMs)) {
                subscriberConnection = connection;
                connection.send("SUBSCRIBE", settings.channel(), settings.controlChannel());
                Object confirmation = connection.read();
                if (!(confirmation instanceof List<?> list) || list.isEmpty() || !"subscribe".equals(list.get(0))) {
                    throw new RedisProtocolException(ConsoleText.tr("Confirmation d'abonnement inattendue"));
                }
                subscribed = true;
                lastError = "";
                backoff = MIN_BACKOFF_MS;
                logger.info(ConsoleText.tr("Synchronisation Redis : abonne a {0}", settings.describe()));
                listen(connection);
            } catch (IOException | RuntimeException failure) {
                if (closed) {
                    break;
                }
                subscribed = false;
                lastError = describe(failure);
                logger.log(Level.WARNING,
                        ConsoleText.tr("Synchronisation Redis : liaison d'abonnement perdue ({0}), nouvel essai dans {1} s",
                                lastError, backoff / 1000));
                sleep(backoff);
                backoff = Math.min(backoff * 2, MAX_BACKOFF_MS);
            } finally {
                subscriberConnection = null;
                subscribed = false;
            }
        }
    }

    private void listen(RespConnection connection) throws IOException {
        boolean awaitingPong = false;
        while (!closed) {
            Object reply = connection.readOrTimeout();
            if (reply == RespConnection.TIMEOUT) {
                if (awaitingPong) {
                    throw new IOException(ConsoleText.tr("Aucune reponse au PING de controle"));
                }
                connection.send("PING");
                awaitingPong = true;
                continue;
            }
            awaitingPong = false;
            if (reply instanceof List<?> message && message.size() == 3 && "message".equals(message.get(0))
                    && message.get(2) instanceof String payload) {
                dispatch((String) message.get(1), payload);
            }
        }
    }

    private void dispatch(String channel, String payload) {
        if (channel.equals(settings.controlChannel())) {
            BiConsumer<String, String> control = controlListener;
            if (control != null) {
                SyncPayloads.decodeControl(payload).ifPresent(fields -> {
                    try {
                        control.accept(fields[0], fields[1]);
                    } catch (RuntimeException failure) {
                        logger.log(Level.WARNING,
                                ConsoleText.tr("Synchronisation Redis : la commande de controle a echoue"), failure);
                    }
                });
            }
            return;
        }
        Consumer<SyncEvent> receiver = listener;
        if (receiver == null) {
            return;
        }
        SyncPayloads.decode(payload).ifPresentOrElse(event -> {
            try {
                receiver.accept(event);
            } catch (RuntimeException failure) {
                logger.log(Level.WARNING, ConsoleText.tr("Synchronisation Redis : le receveur a echoue"), failure);
            }
        }, () -> logger.fine(ConsoleText.tr("Synchronisation Redis : message ignore (format invalide)")));
    }

    private void publishLoop() {
        long backoff = MIN_BACKOFF_MS;
        RespConnection connection = null;
        while (!closed) {
            String[] payload;
            try {
                payload = outbox.poll(1, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                break;
            }
            if (payload == null) {
                continue;
            }
            try {
                if (connection == null) {
                    connection = RespConnection.open(settings, settings.socketTimeoutMs());
                    publisherConnection = connection;
                }
                connection.send("PUBLISH", payload[0], payload[1]);
                if (!(connection.read() instanceof Long)) {
                    throw new RedisProtocolException(ConsoleText.tr("Reponse inattendue a PUBLISH"));
                }
                backoff = MIN_BACKOFF_MS;
            } catch (IOException | RuntimeException failure) {
                closeQuietly(connection);
                connection = null;
                publisherConnection = null;
                if (closed) {
                    break;
                }
                backingOff = true;
                lastError = describe(failure);
                outbox.clear();
                logger.log(Level.WARNING,
                        ConsoleText.tr("Synchronisation Redis : publication impossible ({0}), les autres serveurs se rattraperont par le stockage",
                                lastError));
                sleep(backoff);
                backoff = Math.min(backoff * 2, MAX_BACKOFF_MS);
                // Une nouvelle tentative est autorisee : le prochain evenement rouvrira la connexion.
                backingOff = false;
            }
        }
        closeQuietly(connection);
    }

    /** Message sans secret : type d'erreur et texte du pilote reseau ou de Redis (jamais le mot de passe). */
    private static String describe(Exception failure) {
        String message = failure.getMessage();
        return failure.getClass().getSimpleName() + (message == null ? "" : " : " + message);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static void closeQuietly(RespConnection connection) {
        if (connection != null) {
            connection.close();
        }
    }

    private static void join(Thread thread) {
        try {
            thread.join(2_000);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
