package dev.novadev.novabank.bukkit.discord;

import dev.novadev.novabank.config.model.DiscordSettings;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Envoi d'un message dans le salon, par le webhook du salon ou par un bot (API REST v10 de Discord,
 * {@code POST /channels/<id>/messages} avec {@code Authorization: Bot <jeton>}). HTTP du JDK : aucune bibliotheque.
 */
final class DiscordSender implements DiscordTransport {

    static final String API = "https://discord.com/api/v10/channels/";

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final String userAgent;

    /** @param version version de NovaBank, pour l'en-tete {@code User-Agent} exige par Discord */
    DiscordSender(String version) {
        this.userAgent = "DiscordBot (https://github.com/YoannFm/NovaBank, " + version + ")";
    }

    /**
     * @param settings reglages en vigueur
     * @param body     corps JSON du message ({@link DiscordText#json})
     * @return la reponse de Discord (statut et corps)
     */
    @Override
    public CompletableFuture<DiscordTransport.Reply> send(DiscordSettings settings, String body) {
        HttpRequest.Builder request = HttpRequest.newBuilder(target(settings))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("User-Agent", userAgent)
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (settings.mode() == DiscordSettings.Mode.BOT) {
            request.header("Authorization", "Bot " + settings.botToken().reveal());
        }
        return http.sendAsync(request.build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> new DiscordTransport.Reply(response.statusCode(), response.body()));
    }

    static URI target(DiscordSettings settings) {
        return URI.create(settings.mode() == DiscordSettings.Mode.BOT
                ? API + settings.channelId() + "/messages" : settings.webhookUrl().reveal());
    }
}
