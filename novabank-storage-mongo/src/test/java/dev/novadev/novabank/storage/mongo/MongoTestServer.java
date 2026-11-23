package dev.novadev.novabank.storage.mongo;

import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Base de test sur un vrai serveur MongoDB, au nom aleatoire, supprimee a la fermeture avec les
 * backends ouverts dessus.
 *
 * <p>Serveur designe par les variables exportees par {@code scripts/db/with-db.sh mongodb|mongodb-rs}
 * (ou les proprietes systeme {@code novabank.it.db.<cle>}) : {@code uri}, {@code product},
 * {@code ctl} (script {@code db-server.sh}, tests de panne). Sans URI, les tests
 * {@link RequiresMongoServer} sont ignores.
 */
final class MongoTestServer implements AutoCloseable {

    /** Tag JUnit des tests sur serveur MongoDB. */
    static final String TAG = "mongodb-server";

    /** Journal des backends de test. */
    static final Logger LOGGER = Logger.getLogger("NovaBank-test-mongo");

    private final String database;
    private final List<MongoBackend> opened = new ArrayList<>();

    private MongoTestServer(String database) {
        this.database = database;
    }

    static boolean available() {
        return setting("uri") != null && product().startsWith("mongodb");
    }

    static boolean controllable() {
        return available() && setting("ctl") != null;
    }

    static String product() {
        return String.valueOf(setting("product")).toLowerCase(Locale.ROOT);
    }

    /**
     * @return {@code true} si le serveur de test est un replica set (transactions)
     */
    static boolean replicaSet() {
        return "mongodb-rs".equals(product());
    }

    static MongoTestServer create() {
        return new MongoTestServer("novabank_it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
    }

    String database() {
        return database;
    }

    MongoConnectionSettings settings() {
        return settings(3_000);
    }

    /**
     * @param serverSelectionTimeoutMs delai de recherche d'un serveur utilisable
     * @return les reglages de la base de test
     */
    MongoConnectionSettings settings(int serverSelectionTimeoutMs) {
        return new MongoConnectionSettings(new ConnectionString(setting("uri")), database, 2_000,
                serverSelectionTimeoutMs, 10_000);
    }

    /**
     * @return un backend (un client) en mode automatique, ferme avec cette base
     */
    MongoBackend open() {
        return track(MongoBackend.open(settings(), Clock.systemUTC(), LOGGER));
    }

    /**
     * @param mode    strategie d'ecriture imposee
     * @param budget  budget de contention
     * @return un backend (un client), ferme avec cette base
     */
    MongoBackend open(MongoBackend.WriteMode mode, Duration budget) {
        return open(mode, budget, settings());
    }

    /**
     * @param mode     strategie d'ecriture imposee
     * @param budget   budget de contention
     * @param settings reglages de connexion
     * @return un backend (un client), ferme avec cette base
     */
    MongoBackend open(MongoBackend.WriteMode mode, Duration budget, MongoConnectionSettings settings) {
        return track(MongoBackend.open(settings, Clock.systemUTC(), LOGGER, budget, mode));
    }

    /**
     * @return un client direct pour les verifications en base (a fermer)
     */
    static MongoClient rawClient() {
        return MongoClients.create(setting("uri"));
    }

    /**
     * Arrete ({@code kill}, {@code stop}) ou relance ({@code start}) le serveur.
     */
    static void control(String action) throws IOException, InterruptedException {
        Process process = new ProcessBuilder("bash", setting("ctl"), product(), action).redirectErrorStream(true)
                .start();
        if (!process.waitFor(120, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("db-server.sh " + action + " : delai depasse");
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.exitValue() != 0) {
            throw new IllegalStateException("db-server.sh " + action + " en echec : " + output);
        }
    }

    @Override
    public void close() {
        for (MongoBackend backend : opened) {
            backend.close();
        }
        try (MongoClient client = rawClient()) {
            MongoDatabase target = client.getDatabase(database);
            target.drop();
        }
    }

    private MongoBackend track(MongoBackend backend) {
        opened.add(backend);
        return backend;
    }

    private static String setting(String key) {
        String property = System.getProperty("novabank.it.db." + key);
        return property != null ? property : System.getenv("NOVABANK_IT_DB_" + key.toUpperCase(Locale.ROOT));
    }
}
