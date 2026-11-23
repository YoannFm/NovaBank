package dev.novadev.novabank.bukkit.discord;

import dev.novadev.novabank.api.operation.OperationType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Mise en forme d'un message Discord : echappement, regroupement par joueur, encadres colores, corps JSON. */
final class DiscordText {

    /** Encadres par message, limite de Discord. */
    static final int MAX_EMBEDS = 10;

    /** Texte d'un encadre, marge comprise (limite Discord : 4096). */
    static final int MAX_DESCRIPTION = 4000;

    /** Texte cumule des encadres d'un message, marge comprise (limite Discord : 6000). */
    static final int MAX_MESSAGE = 5500;

    /** Couleurs des encadres. */
    static final int GREEN = 0x2ECC71;
    static final int RED = 0xE74C3C;
    static final int ORANGE = 0xE67E22;
    static final int BLUE = 0x3498DB;

    private static final Pattern RETRY_AFTER = Pattern.compile("\"retry_after\"\\s*:\\s*([0-9.]+)");

    private DiscordText() {
    }

    /**
     * Operation a annoncer, noms deja echappes.
     *
     * @param type     depot, retrait ou reset
     * @param player   joueur a l'origine
     * @param bank     nom de la banque
     * @param item     nom de l'item, vide pour un reset
     * @param quantity unites deplacees
     * @param value    variation de valeur, en valeur absolue
     */
    record Announce(OperationType type, String player, String bank, String item, long quantity, BigDecimal value) {
    }

    /**
     * Operations d'un meme joueur, de meme type, sur une meme banque.
     *
     * @param type   type commun
     * @param player joueur
     * @param bank   banque
     * @param items  unites par item, dans l'ordre d'arrivee
     * @param value  variation de valeur cumulee
     */
    record Group(OperationType type, String player, String bank, Map<String, Long> items, BigDecimal value) {
    }

    /**
     * Encadre d'un message.
     *
     * @param description texte (Markdown de Discord)
     * @param color       couleur RGB
     */
    record Embed(String description, int color) {

        /** @param type type d'operation @return sa couleur */
        static int color(OperationType type) {
            return switch (type) {
                case DEPOSIT -> GREEN;
                case WITHDRAW -> RED;
                default -> ORANGE;
            };
        }
    }

    /**
     * Regroupe les operations d'un meme joueur sur une meme banque (un "Tout deposer" donne une seule ligne).
     *
     * @param announces operations, dans l'ordre
     * @return les groupes, dans l'ordre de leur premiere operation
     */
    static List<Group> group(List<Announce> announces) {
        Map<List<Object>, Group> groups = new LinkedHashMap<>();
        for (Announce announce : announces) {
            List<Object> key = List.of(announce.type(), announce.player(), announce.bank());
            Group group = groups.computeIfAbsent(key, ignored -> new Group(announce.type(), announce.player(),
                    announce.bank(), new LinkedHashMap<>(), BigDecimal.ZERO));
            if (!announce.item().isEmpty()) {
                group.items().merge(announce.item(), announce.quantity(), Long::sum);
            }
            groups.put(key, new Group(group.type(), group.player(), group.bank(), group.items(),
                    group.value().add(announce.value())));
        }
        return List.copyOf(groups.values());
    }

    /**
     * Neutralise le Markdown et les mentions d'un nom venu du jeu (joueur, ile, item) : un joueur nomme {@code @everyone}
     * ou {@code **x**} n'est jamais interprete.
     *
     * @param text texte du jeu
     * @return le texte echappe
     */
    static String escape(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            if ("\\*_~`|>#[]()".indexOf(c) >= 0) {
                out.append('\\');
            }
            out.append(c);
            if (c == '@') {
                out.append('​');
            }
        }
        return out.toString();
    }

    /**
     * @param embeds encadres a envoyer, dans l'ordre
     * @return corps JSON des messages : au plus {@link #MAX_EMBEDS} encadres et {@link #MAX_MESSAGE} caracteres chacun,
     *         un texte trop long etant coupe a {@link #MAX_DESCRIPTION}
     */
    static List<String> payloads(List<Embed> embeds) {
        List<String> messages = new ArrayList<>();
        List<Embed> current = new ArrayList<>();
        int length = 0;
        for (Embed embed : embeds) {
            String text = embed.description();
            Embed safe = text.length() > MAX_DESCRIPTION ? new Embed(text.substring(0, MAX_DESCRIPTION), embed.color())
                    : embed;
            if (!current.isEmpty() && (current.size() == MAX_EMBEDS
                    || length + safe.description().length() > MAX_MESSAGE)) {
                messages.add(json(current));
                current.clear();
                length = 0;
            }
            current.add(safe);
            length += safe.description().length();
        }
        if (!current.isEmpty()) {
            messages.add(json(current));
        }
        return messages;
    }

    /**
     * @param embeds encadres d'un message
     * @return le corps JSON, mentions desactivees
     */
    static String json(List<Embed> embeds) {
        StringBuilder out = new StringBuilder("{\"embeds\":[");
        for (int i = 0; i < embeds.size(); i++) {
            Embed embed = Objects.requireNonNull(embeds.get(i));
            out.append(i == 0 ? "" : ",").append("{\"description\":\"");
            quote(embed.description(), out);
            out.append("\",\"color\":").append(embed.color()).append('}');
        }
        return out.append("],\"allowed_mentions\":{\"parse\":[]}}").toString();
    }

    private static void quote(String text, StringBuilder out) {
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
    }

    /**
     * @param body reponse de Discord a une limite de debit (HTTP 429)
     * @return le delai demande en secondes ({@code retry_after}), 5 s s'il est illisible
     */
    static double retryAfter(String body) {
        Matcher matcher = RETRY_AFTER.matcher(body == null ? "" : body);
        return matcher.find() ? Double.parseDouble(matcher.group(1)) : 5.0;
    }
}
