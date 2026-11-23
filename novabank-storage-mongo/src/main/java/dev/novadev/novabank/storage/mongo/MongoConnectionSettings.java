package dev.novadev.novabank.storage.mongo;

import com.mongodb.ConnectionString;
import dev.novadev.novabank.core.storage.BackendParameters;
import dev.novadev.novabank.core.text.ConsoleText;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Reglages de connexion lus dans les {@link BackendParameters}.
 *
 * <p>Cles : {@value #URI} (secret : peut contenir les identifiants), {@value #DATABASE},
 * {@value #CONNECT_TIMEOUT_MS}, {@value #SERVER_SELECTION_TIMEOUT_MS} et {@value #SOCKET_TIMEOUT_MS}
 * (millisecondes).
 *
 * @param uri                      URI de connexion
 * @param database                 nom de la base
 * @param connectTimeoutMs         delai d'etablissement d'une connexion
 * @param serverSelectionTimeoutMs delai maximal pour trouver un serveur utilisable (primaire)
 * @param socketTimeoutMs          delai maximal d'attente d'une reponse
 */
public record MongoConnectionSettings(ConnectionString uri, String database, int connectTimeoutMs,
                                      int serverSelectionTimeoutMs, int socketTimeoutMs) {

    /** URI de connexion. */
    public static final String URI = "uri";
    /** Nom de la base. */
    public static final String DATABASE = "database";
    /** Delai de connexion, en millisecondes. */
    public static final String CONNECT_TIMEOUT_MS = "connect-timeout-ms";
    /** Delai de selection du serveur, en millisecondes. */
    public static final String SERVER_SELECTION_TIMEOUT_MS = "server-selection-timeout-ms";
    /** Delai de reponse, en millisecondes. */
    public static final String SOCKET_TIMEOUT_MS = "socket-timeout-ms";

    private static final Pattern DATABASE_NAME = Pattern.compile("[A-Za-z0-9_-]{1,63}");
    private static final int MIN_TIMEOUT_MS = 100;
    private static final int MAX_TIMEOUT_MS = 300_000;

    /**
     * @throws NullPointerException si un champ est nul
     */
    public MongoConnectionSettings {
        Objects.requireNonNull(uri, "uri");
        Objects.requireNonNull(database, "database");
    }

    /**
     * @param parameters parametres du backend
     * @return les reglages valides
     * @throws IllegalArgumentException si un reglage manque ou est invalide ; le message ne
     *                                  contient jamais l'URI
     */
    public static MongoConnectionSettings from(BackendParameters parameters) {
        ConnectionString uri;
        try {
            uri = new ConnectionString(parameters.text(URI));
        } catch (IllegalArgumentException invalid) {
            // Le message du pilote peut reprendre l'URI (identifiants) : il n'est pas transmis.
            throw new IllegalArgumentException(
                    ConsoleText.tr("Parametre de stockage {0} : URI MongoDB invalide (attendu : mongodb://... ou mongodb+srv://...)",
                            URI));
        }
        String database = parameters.text(DATABASE);
        if (!DATABASE_NAME.matcher(database).matches()) {
            throw new IllegalArgumentException(ConsoleText.tr("Parametre de stockage {0} invalide : {1}",
                    DATABASE, database));
        }
        return new MongoConnectionSettings(uri, database,
                parameters.integer(CONNECT_TIMEOUT_MS, MIN_TIMEOUT_MS, MAX_TIMEOUT_MS),
                parameters.integer(SERVER_SELECTION_TIMEOUT_MS, MIN_TIMEOUT_MS, MAX_TIMEOUT_MS),
                parameters.integer(SOCKET_TIMEOUT_MS, MIN_TIMEOUT_MS, MAX_TIMEOUT_MS));
    }

    /**
     * @return hotes et base, sans identifiants (journaux)
     */
    public String describe() {
        return String.join(",", uri.getHosts()) + "/" + database;
    }

    /**
     * @return le mot de passe de l'URI, ou vide
     */
    String password() {
        char[] password = uri.getPassword();
        return password == null ? "" : new String(password);
    }

    @Override
    public String toString() {
        return "MongoConnectionSettings{" + describe() + "}";
    }
}
