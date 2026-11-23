package dev.novadev.novabank.sync.redis;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * Serveur RESP minimal de test : lit des commandes, repond par une fonction du test, peut couper
 * les connexions. Sert aux cas que Redis reel ne produit pas (reponses invalides, authentification).
 */
final class FakeRedisServer implements AutoCloseable {

    private final ServerSocket server;
    private final Function<List<String>, byte[]> handler;
    private final List<Socket> clients = new CopyOnWriteArrayList<>();
    final List<List<String>> commands = new CopyOnWriteArrayList<>();
    private volatile boolean running = true;

    FakeRedisServer(Function<List<String>, byte[]> handler) throws IOException {
        this.server = new ServerSocket(0, 10, InetAddress.getLoopbackAddress());
        this.handler = handler;
        Thread acceptor = new Thread(this::acceptLoop, "fake-redis-accept");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    int port() {
        return server.getLocalPort();
    }

    static byte[] simple(String text) {
        return ("+" + text + "\r\n").getBytes(StandardCharsets.UTF_8);
    }

    static byte[] error(String text) {
        return ("-" + text + "\r\n").getBytes(StandardCharsets.UTF_8);
    }

    static byte[] integer(long value) {
        return (":" + value + "\r\n").getBytes(StandardCharsets.UTF_8);
    }

    static byte[] bulk(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        return ("$" + bytes.length + "\r\n" + text + "\r\n").getBytes(StandardCharsets.UTF_8);
    }

    static byte[] array(byte[]... items) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(("*" + items.length + "\r\n").getBytes(StandardCharsets.US_ASCII));
        for (byte[] item : items) {
            out.writeBytes(item);
        }
        return out.toByteArray();
    }

    /** Coupe brutalement toutes les connexions ouvertes. */
    void dropClients() {
        for (Socket client : clients) {
            try {
                client.close();
            } catch (IOException ignored) {
                // deja fermee
            }
        }
        clients.clear();
    }

    /** Pousse des octets bruts a tous les clients (message de canal). */
    void push(byte[] bytes) throws IOException {
        for (Socket client : clients) {
            OutputStream out = client.getOutputStream();
            out.write(bytes);
            out.flush();
        }
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket client = server.accept();
                clients.add(client);
                Thread worker = new Thread(() -> serve(client), "fake-redis-client");
                worker.setDaemon(true);
                worker.start();
            } catch (IOException closed) {
                return;
            }
        }
    }

    private void serve(Socket client) {
        try (InputStream in = client.getInputStream()) {
            OutputStream out = client.getOutputStream();
            while (true) {
                List<String> command = readCommand(in);
                if (command == null) {
                    return;
                }
                commands.add(command);
                byte[] reply = handler.apply(command);
                if (reply != null) {
                    out.write(reply);
                    out.flush();
                }
            }
        } catch (IOException ignored) {
            // client parti
        }
    }

    private static List<String> readCommand(InputStream in) throws IOException {
        int type = in.read();
        if (type < 0) {
            return null;
        }
        int count = Integer.parseInt(line(in));
        List<String> arguments = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            in.read();
            int length = Integer.parseInt(line(in));
            byte[] bytes = in.readNBytes(length);
            in.readNBytes(2);
            arguments.add(new String(bytes, StandardCharsets.UTF_8));
        }
        return arguments;
    }

    private static String line(InputStream in) throws IOException {
        StringBuilder text = new StringBuilder();
        int next;
        while ((next = in.read()) != '\r') {
            if (next < 0) {
                throw new IOException("fin de flux");
            }
            text.append((char) next);
        }
        in.read();
        return text.toString();
    }

    @Override
    public void close() throws IOException {
        running = false;
        dropClients();
        server.close();
    }
}
