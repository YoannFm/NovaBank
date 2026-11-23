package dev.novadev.novabank.sync.redis;

import java.io.IOException;

/**
 * Reponse Redis invalide ou erreur renvoyee par le serveur (le message ne contient jamais de secret).
 */
final class RedisProtocolException extends IOException {

    private static final long serialVersionUID = 1L;

    RedisProtocolException(String message) {
        super(message);
    }
}
