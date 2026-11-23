package dev.novadev.novabank.sync.redis;

import dev.novadev.novabank.core.text.ConsoleText;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Connexion Redis au protocole RESP2 : commandes en tableaux de chaines, reponses lues avec des
 * bornes strictes (taille des chaines et des tableaux) pour qu'un serveur defaillant ne puisse pas
 * saturer la memoire. Non sure entre threads : un lecteur et un ecrivain au plus.
 */
final class RespConnection implements AutoCloseable {

    /** Reponse absente : delai ecoule avant le premier octet d'une reponse (rien n'a ete consomme). */
    static final Object TIMEOUT = new Object();

    private static final int MAX_BULK_BYTES = 1 << 20;
    private static final int MAX_ARRAY_LENGTH = 64;
    private static final int MAX_LINE_BYTES = 4096;
    private static final int MAX_NESTING = 4;

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;

    private RespConnection(Socket socket) throws IOException {
        this.socket = socket;
        this.in = new BufferedInputStream(socket.getInputStream(), 8192);
        this.out = socket.getOutputStream();
    }

    /**
     * Ouvre la connexion et s'authentifie si un mot de passe est configure.
     *
     * @param settings      cible
     * @param readTimeoutMs delai de lecture d'une reponse
     * @return la connexion prete
     * @throws IOException si la connexion, le TLS ou l'authentification echoue
     */
    static RespConnection open(RedisConnectionSettings settings, int readTimeoutMs) throws IOException {
        Socket socket = new Socket();
        try {
            socket.setTcpNoDelay(true);
            socket.setKeepAlive(true);
            socket.connect(new InetSocketAddress(settings.host(), settings.port()), settings.connectTimeoutMs());
            socket.setSoTimeout(settings.socketTimeoutMs());
            if (settings.ssl()) {
                SSLSocket tls = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault())
                        .createSocket(socket, settings.host(), settings.port(), true);
                SSLParameters parameters = tls.getSSLParameters();
                parameters.setEndpointIdentificationAlgorithm("HTTPS");
                tls.setSSLParameters(parameters);
                tls.startHandshake();
                socket = tls;
            }
            RespConnection connection = new RespConnection(socket);
            if (!settings.password().isEmpty()) {
                connection.send("AUTH", settings.password());
                Object reply = connection.read();
                if (!"OK".equals(reply)) {
                    throw new RedisProtocolException(ConsoleText.tr("Reponse inattendue a AUTH"));
                }
            }
            socket.setSoTimeout(readTimeoutMs);
            return connection;
        } catch (IOException | RuntimeException failure) {
            try {
                socket.close();
            } catch (IOException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    /**
     * Envoie une commande.
     *
     * @param arguments commande et arguments (texte UTF-8)
     * @throws IOException si l'ecriture echoue
     */
    void send(String... arguments) throws IOException {
        ByteArrayOutputStream frame = new ByteArrayOutputStream(64);
        frame.writeBytes(("*" + arguments.length + "\r\n").getBytes(StandardCharsets.US_ASCII));
        for (String argument : arguments) {
            byte[] bytes = argument.getBytes(StandardCharsets.UTF_8);
            frame.writeBytes(("$" + bytes.length + "\r\n").getBytes(StandardCharsets.US_ASCII));
            frame.writeBytes(bytes);
            frame.writeBytes(new byte[] {'\r', '\n'});
        }
        out.write(frame.toByteArray());
        out.flush();
    }

    /**
     * Lit une reponse complete.
     *
     * @return {@link String}, {@link Long}, {@link List} ou {@code null} (reponse nulle)
     * @throws RedisProtocolException si le serveur repond une erreur ou une trame invalide
     * @throws IOException            si la lecture echoue ou si le delai est depasse
     */
    Object read() throws IOException {
        int type = in.read();
        if (type < 0) {
            throw new IOException(ConsoleText.tr("Connexion fermee par le serveur"));
        }
        return parse(type, 0);
    }

    /**
     * Comme {@link #read()}, mais un delai ecoule AVANT le premier octet rend {@link #TIMEOUT}
     * sans rien consommer (la connexion reste saine). Un delai ecoule au milieu d'une reponse
     * reste une erreur : le flux serait desynchronise.
     *
     * @return la reponse ou {@link #TIMEOUT}
     * @throws IOException comme {@link #read()}
     */
    Object readOrTimeout() throws IOException {
        int type;
        try {
            type = in.read();
        } catch (SocketTimeoutException idle) {
            return TIMEOUT;
        }
        if (type < 0) {
            throw new IOException(ConsoleText.tr("Connexion fermee par le serveur"));
        }
        return parse(type, 0);
    }

    private Object parse(int type, int depth) throws IOException {
        switch (type) {
            case '+':
                return readLine();
            case '-':
                throw new RedisProtocolException(ConsoleText.tr("Erreur Redis : {0}", sanitize(readLine())));
            case ':':
                return parseLong(readLine());
            case '$':
                return readBulk(parseLong(readLine()));
            case '*':
                return readArray(parseLong(readLine()), depth);
            default:
                throw new RedisProtocolException(ConsoleText.tr("Trame Redis inconnue : type {0}", type));
        }
    }

    private Object readBulk(long length) throws IOException {
        if (length < 0) {
            return null;
        }
        if (length > MAX_BULK_BYTES) {
            throw new RedisProtocolException(ConsoleText.tr("Chaine Redis trop longue : {0}", length));
        }
        byte[] bytes = in.readNBytes((int) length);
        if (bytes.length != length || in.read() != '\r' || in.read() != '\n') {
            throw new RedisProtocolException(ConsoleText.tr("Chaine Redis tronquee"));
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private Object readArray(long length, int depth) throws IOException {
        if (length < 0) {
            return null;
        }
        if (length > MAX_ARRAY_LENGTH || depth >= MAX_NESTING) {
            throw new RedisProtocolException(ConsoleText.tr("Tableau Redis trop grand ou trop imbrique"));
        }
        List<Object> items = new ArrayList<>((int) length);
        for (long i = 0; i < length; i++) {
            int type = in.read();
            if (type < 0) {
                throw new IOException(ConsoleText.tr("Connexion fermee par le serveur"));
            }
            items.add(parse(type, depth + 1));
        }
        return items;
    }

    private String readLine() throws IOException {
        StringBuilder line = new StringBuilder();
        while (true) {
            int next = in.read();
            if (next < 0) {
                throw new IOException(ConsoleText.tr("Connexion fermee par le serveur"));
            }
            if (next == '\r') {
                if (in.read() != '\n') {
                    throw new RedisProtocolException(ConsoleText.tr("Fin de ligne Redis invalide"));
                }
                return line.toString();
            }
            if (line.length() >= MAX_LINE_BYTES) {
                throw new RedisProtocolException(ConsoleText.tr("Ligne Redis trop longue"));
            }
            line.append((char) next);
        }
    }

    private static long parseLong(String text) throws RedisProtocolException {
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException invalid) {
            throw new RedisProtocolException(ConsoleText.tr("Entier Redis invalide"));
        }
    }

    /** Un message d'erreur Redis ne devrait pas contenir de secret ; il est borne et ses controles retires. */
    private static String sanitize(String text) {
        String clean = text.replaceAll("\\p{Cntrl}", " ");
        return clean.length() > 120 ? clean.substring(0, 120) : clean;
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
            // Fermeture au mieux : la connexion est de toute facon abandonnee.
        }
    }
}
