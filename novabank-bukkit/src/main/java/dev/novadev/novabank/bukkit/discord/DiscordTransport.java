package dev.novadev.novabank.bukkit.discord;

import dev.novadev.novabank.config.model.DiscordSettings;
import java.util.concurrent.CompletableFuture;

/** Envoi d'un message dans le salon ({@link DiscordSender}) ; remplace par un faux dans les tests. */
@FunctionalInterface
interface DiscordTransport {

    /**
     * Reponse de Discord.
     *
     * @param status statut HTTP
     * @param body   corps (delai demande en cas de limite de debit)
     */
    record Reply(int status, String body) {
    }

    /**
     * @param settings reglages en vigueur
     * @param body     corps JSON du message
     * @return la reponse de Discord
     */
    CompletableFuture<Reply> send(DiscordSettings settings, String body);
}
