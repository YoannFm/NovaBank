package dev.novadev.novabank.sync.redis;

import dev.novadev.novabank.core.text.ConsoleText;
import java.util.Objects;

/**
 * Reglages de connexion au serveur Redis. Le mot de passe n'apparait jamais dans
 * {@link #toString()}, {@link #describe()} ni dans les messages d'erreur du module.
 *
 * @param host             hote
 * @param port             port TCP
 * @param password         mot de passe, vide si aucun
 * @param ssl              connexion TLS (certificat et nom d'hote verifies)
 * @param channelPrefix    prefixe du canal de publication
 * @param connectTimeoutMs delai maximal de connexion
 * @param socketTimeoutMs  delai maximal d'une reponse
 */
public record RedisConnectionSettings(String host, int port, String password, boolean ssl, String channelPrefix,
                                      int connectTimeoutMs, int socketTimeoutMs) {

    /**
     * @throws NullPointerException     si un texte est nul
     * @throws IllegalArgumentException si le port ou un delai est hors bornes ou si l'hote ou le prefixe est vide
     */
    public RedisConnectionSettings {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(password, "password");
        Objects.requireNonNull(channelPrefix, "channelPrefix");
        if (host.isBlank() || channelPrefix.isBlank() || port < 1 || port > 65_535 || connectTimeoutMs <= 0
                || socketTimeoutMs <= 0) {
            throw new IllegalArgumentException(ConsoleText.tr("Reglages Redis invalides : {0}",
                    describe(host, port, ssl)));
        }
    }

    /**
     * @return le canal des changements de banque
     */
    public String channel() {
        return channelPrefix + ":bank-changes";
    }

    /**
     * @return le canal des commandes de controle entre serveurs
     */
    public String controlChannel() {
        return channelPrefix + ":control";
    }

    /**
     * @return la cible lisible, sans secret
     */
    public String describe() {
        return describe(host, port, ssl);
    }

    @Override
    public String toString() {
        return "RedisConnectionSettings{" + describe() + ", password=****}";
    }

    private static String describe(String host, int port, boolean ssl) {
        return host + ":" + port + (ssl ? " (TLS)" : "");
    }
}
