package dev.novadev.novabank.config.model;

import java.util.Objects;

/**
 * Section {@code redis} : synchronisation multi-serveur.
 *
 * @param enabled          synchronisation active
 * @param host             hote Redis
 * @param port             port TCP
 * @param password         mot de passe (secret, vide si aucun)
 * @param database         index de base Redis
 * @param ssl              connexion TLS
 * @param channelPrefix    prefixe des canaux de publication
 * @param connectTimeoutMs delai maximal de connexion, en millisecondes
 * @param socketTimeoutMs  delai maximal d'une commande, en millisecondes
 * @param serverName       nom de ce serveur dans le reseau (diagnostic) ; l'identifiant technique ajoute un
 *                         suffixe aleatoire a chaque demarrage, deux serveurs au meme nom ne se genent donc pas
 * @param pollIntervalSeconds periode de reconciliation avec le stockage, en secondes
 * @param overlapSeconds   recouvrement des lectures de reconciliation (tolerance d'ecart d'horloge), en secondes
 * @param deployConfig     deploiement de la configuration en jeu : ce serveur envoie ses changements aux autres et applique
 *                         ceux qu'ils envoient (sections partagees seulement, jamais storage, redis, server ni secret)
 */
public record RedisSettings(boolean enabled, String host, int port, Secret password, int database, boolean ssl,
                            String channelPrefix, int connectTimeoutMs, int socketTimeoutMs,
                            String serverName, int pollIntervalSeconds, int overlapSeconds, boolean deployConfig) {

    /**
     * Verifie la presence des champs.
     *
     * @throws NullPointerException si un champ est nul
     */
    public RedisSettings {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(password, "password");
        Objects.requireNonNull(channelPrefix, "channelPrefix");
        Objects.requireNonNull(serverName, "serverName");
    }
}
