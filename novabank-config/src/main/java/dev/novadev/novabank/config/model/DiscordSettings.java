package dev.novadev.novabank.config.model;

import dev.novadev.novabank.api.operation.OperationType;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Salon Discord des operations (section {@code discord}, propre a chaque serveur : jamais deployee, secrets compris).
 *
 * @param enabled      envoi actif
 * @param mode         webhook du salon, ou bot qui ecrit dans un salon
 * @param webhookUrl   adresse du webhook (secret)
 * @param botToken     jeton du bot (secret)
 * @param channelId    identifiant du salon du bot
 * @param events       operations annoncees
 * @param bankTypes    types de banque annonces, vide = tous
 * @param minQuantity  quantite minimale d'un depot ou d'un retrait pour etre annonce
 * @param batchSeconds regroupement : un message Discord au plus toutes les N secondes
 */
public record DiscordSettings(boolean enabled, Mode mode, Secret webhookUrl, Secret botToken, String channelId,
                              Set<OperationType> events, List<String> bankTypes, long minQuantity, int batchSeconds) {

    /** Facon d'ecrire dans le salon. */
    public enum Mode {
        /** Webhook du salon (Parametres du salon, Integrations, Webhooks). */
        WEBHOOK,
        /** Bot Discord avec le droit d'envoyer des messages dans le salon. */
        BOT
    }

    /**
     * @throws NullPointerException si un champ objet est nul
     */
    public DiscordSettings {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(webhookUrl, "webhookUrl");
        Objects.requireNonNull(botToken, "botToken");
        Objects.requireNonNull(channelId, "channelId");
        events = Set.copyOf(events);
        bankTypes = List.copyOf(bankTypes);
    }

    /** @return la configuration par defaut : desactive */
    public static DiscordSettings disabled() {
        return new DiscordSettings(false, Mode.WEBHOOK, Secret.of(""), Secret.of(""), "",
                Set.of(OperationType.DEPOSIT, OperationType.WITHDRAW), List.of(), 1, 5);
    }
}
