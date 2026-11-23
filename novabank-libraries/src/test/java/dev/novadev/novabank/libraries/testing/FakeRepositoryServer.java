package dev.novadev.novabank.libraries.testing;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.novadev.novabank.libraries.artifact.LibraryArtifact;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Faux depot Maven sur un port ephemere local. Chaque chemin sert une suite de reponses
 * (la derniere se repete) et compte les requetes recues.
 */
public final class FakeRepositoryServer implements AutoCloseable {

    /** Reponse a une requete. */
    @FunctionalInterface
    public interface Response {
        void send(HttpExchange exchange, CountDownLatch release) throws IOException, InterruptedException;
    }

    private static final long HANG_LIMIT_SECONDS = 30;

    private final HttpServer server;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final CountDownLatch release = new CountDownLatch(1);
    private final Map<String, List<Response>> routes = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> counters = new ConcurrentHashMap<>();
    private final AtomicInteger total = new AtomicInteger();

    public FakeRepositoryServer() {
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        server.setExecutor(executor);
        server.createContext("/", this::handle);
        server.start();
    }

    /**
     * @return l'URL de base du depot ({@code http://127.0.0.1:port/repo/})
     */
    public URI baseUri() {
        return URI.create("http://" + server.getAddress().getAddress().getHostAddress() + ':'
                + server.getAddress().getPort() + "/repo/");
    }

    public void serve(LibraryArtifact artifact, Response... responses) {
        routes.put(path(artifact), List.of(responses));
    }

    public int requests(LibraryArtifact artifact) {
        AtomicInteger counter = counters.get(path(artifact));
        return counter == null ? 0 : counter.get();
    }

    public int totalRequests() {
        return total.get();
    }

    @Override
    public void close() {
        release.countDown();
        server.stop(0);
        executor.shutdownNow();
    }

    // ----- reponses -----

    public static Response ok(byte[] body) {
        return (exchange, latch) -> {
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        };
    }

    /** Corps envoye sans longueur annoncee (encodage par morceaux). */
    public static Response chunked(byte[] body) {
        return (exchange, latch) -> {
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        };
    }

    public static Response delayed(long millis, Response then) {
        return (exchange, latch) -> {
            Thread.sleep(millis);
            then.send(exchange, latch);
        };
    }

    public static Response status(int code) {
        return (exchange, latch) -> {
            exchange.sendResponseHeaders(code, -1);
            exchange.close();
        };
    }

    /** N'envoie jamais les en-tetes. */
    public static Response hangBeforeHeaders() {
        return (exchange, latch) -> {
            latch.await(HANG_LIMIT_SECONDS, TimeUnit.SECONDS);
            exchange.close();
        };
    }

    /** Annonce le corps complet, en envoie une partie puis se bloque. */
    public static Response hangAfterPartialBody(byte[] body) {
        return (exchange, latch) -> {
            exchange.sendResponseHeaders(200, body.length);
            OutputStream out = exchange.getResponseBody();
            out.write(body, 0, body.length / 2);
            out.flush();
            latch.await(HANG_LIMIT_SECONDS, TimeUnit.SECONDS);
            exchange.close();
        };
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        total.incrementAndGet();
        int index = counters.computeIfAbsent(path, key -> new AtomicInteger()).getAndIncrement();
        List<Response> responses = routes.get(path);
        try {
            if (responses == null) {
                status(404).send(exchange, release);
            } else {
                responses.get(Math.min(index, responses.size() - 1)).send(exchange, release);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            exchange.close();
        } catch (IOException clientGone) {
            // Le client a abandonne le transfert (attendu dans les tests de delai).
            exchange.close();
        }
    }

    private static String path(LibraryArtifact artifact) {
        return "/repo/" + artifact.repositoryPath();
    }
}
