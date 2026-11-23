package dev.novadev.novabank.bukkit.command;

import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.bukkit.message.Language;
import dev.novadev.novabank.core.ranking.LeaderboardService;
import dev.novadev.novabank.core.ranking.RankedEntry;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * {@code /bank top [type]} : classement d'un type de banque, lu dans le cache en memoire du
 * {@link LeaderboardService} (aucun acces au stockage). Le rang du joueur est ajoute pour les
 * banques personnelles quand il n'est pas dans les lignes affichees.
 */
public final class LeaderboardCommand {

    /** Commande de repli quand les classements ne sont pas assembles (tests). */
    public static final LeaderboardCommand DISABLED = new LeaderboardCommand(null, Map::of, () -> null,
            entity -> "");

    private final LeaderboardService service;
    private final Supplier<Map<String, Integer>> enabledSizes;
    private final Supplier<Language> language;
    private final java.util.function.Function<EntityRef, String> names;

    /**
     * @param service      classements, ou {@code null} s'il n'y en a aucun
     * @param enabledSizes types classes et nombre de lignes affichees
     * @param language     langue en vigueur
     * @param names        nom affichable d'une entite
     */
    public LeaderboardCommand(LeaderboardService service, Supplier<Map<String, Integer>> enabledSizes,
                              Supplier<Language> language, java.util.function.Function<EntityRef, String> names) {
        this.service = service;
        this.enabledSizes = Objects.requireNonNull(enabledSizes, "enabledSizes");
        this.language = Objects.requireNonNull(language, "language");
        this.names = Objects.requireNonNull(names, "names");
    }

    /**
     * @param actor      destinataire
     * @param playerId   joueur qui consulte
     * @param entityType type demande, ou {@code null} pour le premier type classe
     */
    public void show(CommandActor actor, UUID playerId, String entityType) {
        Language text = language.get();
        Map<String, Integer> sizes = enabledSizes.get();
        if (service == null || sizes.isEmpty()) {
            // Sans langue (commande de repli) : texte neutre.
            actor.send(text == null ? "Leaderboards disabled." : text.chat("leaderboard.disabled"));
            return;
        }
        String type = entityType != null ? entityType : sizes.keySet().iterator().next();
        Integer size = sizes.get(type);
        if (size == null) {
            actor.send(text.chat("leaderboard.unknown-type", "type", type, "types", String.join(", ", sizes.keySet())));
            return;
        }
        List<RankedEntry> top = service.top(type, size);
        actor.send(text.chat("leaderboard.header", "type", type, "size", size));
        if (top.isEmpty()) {
            actor.send(text.chat("leaderboard.empty"));
        }
        BankId own = EntityRef.PLAYER.equals(type.contains("#") ? type.substring(0, type.indexOf('#')) : type) ? BankId.forOwner(EntityRef.player(playerId)) : null;
        for (RankedEntry entry : top) {
            actor.send(text.chat(entry.bankId().equals(own) ? "leaderboard.line-self" : "leaderboard.line", "rank",
                    entry.rank(), "name", names.apply(entry.owner()), "value", text.value(entry.value())));
        }
        if (own != null && top.stream().noneMatch(entry -> entry.bankId().equals(own))) {
            Optional<RankedEntry> rank = service.rankOf(type, own);
            actor.send(rank.map(entry -> text.chat("leaderboard.self", "rank", entry.rank(), "value",
                    text.value(entry.value()))).orElseGet(() -> text.chat("leaderboard.unranked")));
        }
    }
}
