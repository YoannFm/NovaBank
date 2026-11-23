package dev.novadev.novabank.storage.sql.pool;

import dev.novadev.novabank.core.text.ConsoleText;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Connexion a un serveur SQL (MySQL / MariaDB).
 *
 * <p>Le mot de passe ne sort de cet objet que vers le pilote (propriete {@code password}) :
 * {@link #toString()} et {@link #describe()} ne le contiennent jamais, et {@link #mask(String)}
 * l'efface d'un message d'erreur avant journalisation (decision D5).
 *
 * @param host     hote
 * @param port     port TCP
 * @param database base
 * @param user     utilisateur
 * @param password mot de passe (peut etre vide)
 * @param options   proprietes supplementaires du pilote (SSL, delais...), hors identifiants
 */
public record ServerConnection(String host, int port, String database, String user, String password,
                               Map<String, String> options) {

    /** Texte qui remplace le mot de passe. */
    public static final String MASK = "****";

    /** Proprietes reservees aux identifiants : refusees dans les options. */
    private static final Set<String> CREDENTIAL_KEYS = Set.of("user", "password");

    /**
     * @throws IllegalArgumentException si une option porte des identifiants
     */
    public ServerConnection {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(database, "database");
        Objects.requireNonNull(user, "user");
        Objects.requireNonNull(password, "password");
        options = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(options, "options")));
        for (String key : options.keySet()) {
            if (CREDENTIAL_KEYS.contains(key.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException(
                        ConsoleText.tr("Option de connexion '{0}' interdite : utiliser storage.sql.username / storage.sql.password",
                                key));
            }
        }
    }

    /**
     * @return hote, port et base, pour les journaux (sans identifiants)
     */
    public String describe() {
        return host + ":" + port + "/" + database;
    }

    /**
     * @param text texte quelconque (message d'erreur du pilote...)
     * @return le texte dont toute occurrence du mot de passe est remplacee par {@value #MASK}
     */
    public String mask(String text) {
        if (text == null || password.isEmpty()) {
            return text;
        }
        return text.replace(password, MASK);
    }

    /**
     * @return la description, sans mot de passe
     */
    @Override
    public String toString() {
        return "ServerConnection[" + user + "@" + describe() + ", options=" + options.keySet() + "]";
    }
}
