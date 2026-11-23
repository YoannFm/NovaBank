package dev.novadev.novabank.bukkit.command;

import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.core.application.Amount;
import dev.novadev.novabank.core.application.BankService;
import dev.novadev.novabank.core.application.BankView;
import dev.novadev.novabank.core.application.ItemChoice;
import dev.novadev.novabank.core.application.QueryStatus;
import dev.novadev.novabank.core.application.TransferStatus;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Actions des joueurs (commande et GUI) : appel de {@link BankService} et envoi des
 * resultats (livres sur le thread du joueur). La GUI passe par les memes methodes que la
 * commande, avec un rappel de fin pour se rafraichir.
 */
public final class PlayerCommandActions implements PlayerActions {

    private static final Runnable NOTHING = () -> { };

    private final BankService service;
    private final Supplier<TransferMessages> messages;
    private final KnownStock stock;
    private final Supplier<List<String>> entityTypes;
    private final Consumer<UUID> menus;
    private final LeaderboardCommand leaderboard;

    /**
     * @param service     operations des joueurs
     * @param messages    textes des resultats, dans la langue et les regles en vigueur
     * @param stock       items connus en banque (completion)
     * @param entityTypes types de banque actives
     * @param menus       ouverture de la GUI de banque d'un joueur (thread du joueur)
     */
    public PlayerCommandActions(BankService service, Supplier<TransferMessages> messages, KnownStock stock,
                                Supplier<List<String>> entityTypes, Consumer<UUID> menus) {
        this(service, messages, stock, entityTypes, menus, LeaderboardCommand.DISABLED);
    }

    /**
     * @param service     operations des joueurs
     * @param messages    textes des resultats, dans la langue et les regles en vigueur
     * @param stock       items connus en banque (completion)
     * @param entityTypes types de banque actives
     * @param menus       ouverture de la GUI de banque d'un joueur (thread du joueur)
     * @param leaderboard commande de classement
     */
    public PlayerCommandActions(BankService service, Supplier<TransferMessages> messages, KnownStock stock,
                                Supplier<List<String>> entityTypes, Consumer<UUID> menus,
                                LeaderboardCommand leaderboard) {
        this.service = Objects.requireNonNull(service, "service");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.stock = Objects.requireNonNull(stock, "stock");
        this.entityTypes = Objects.requireNonNull(entityTypes, "entityTypes");
        this.menus = Objects.requireNonNull(menus, "menus");
        this.leaderboard = Objects.requireNonNull(leaderboard, "leaderboard");
    }

    @Override
    public void deposit(CommandActor actor, UUID playerId, ItemChoice choice, Amount amount) {
        deposit(actor, playerId, choice, amount, NOTHING);
    }

    /**
     * @param actor    destinataire des resultats
     * @param playerId joueur
     * @param choice   items a deposer
     * @param amount   quantite par item
     * @param then     appele apres l'envoi des resultats
     */
    public void deposit(CommandActor actor, UUID playerId, ItemChoice choice, Amount amount, Runnable then) {
        service.deposit(playerId, choice, amount, results -> {
            results.stream()
                    .filter(result -> result.status() == TransferStatus.SUCCESS)
                    .forEach(result -> result.item().ifPresent(item -> stock.add(playerId, item)));
            messages.get().deposit(results).forEach(actor::send);
            then.run();
        });
    }

    @Override
    public void withdraw(CommandActor actor, UUID playerId, ItemKey item, Amount amount) {
        withdraw(actor, playerId, item, amount, NOTHING);
    }

    /**
     * @param actor    destinataire du resultat
     * @param playerId joueur
     * @param item     item a retirer
     * @param amount   quantite
     * @param then     appele apres l'envoi du resultat
     */
    public void withdraw(CommandActor actor, UUID playerId, ItemKey item, Amount amount, Runnable then) {
        service.withdraw(playerId, item, amount, result -> {
            messages.get().withdraw(result).forEach(actor::send);
            then.run();
        });
    }

    @Override
    public void info(CommandActor actor, UUID playerId, String label) {
        view(playerId, view -> messages.get().info(view, label).forEach(actor::send));
    }

    /**
     * Consulte la banque active et met a jour le stock connu (completion).
     *
     * @param playerId joueur
     * @param done     consultation
     */
    public void view(UUID playerId, Consumer<BankView> done) {
        service.info(playerId, view -> {
            view.snapshot().ifPresent(snapshot -> stock.replace(playerId, snapshot.stock().keySet()));
            done.accept(view);
        });
    }

    @Override
    public void select(CommandActor actor, UUID playerId, String entityType) {
        service.select(playerId, entityType, result -> actor.send(messages.get().select(result, entityType,
                entityTypes.get())));
    }

    /**
     * Rend active la banque du type demande sans message, puis execute l'action ; en cas d'echec (pas d'entite de ce
     * type, base indisponible...), le joueur recoit le message du choix de banque et l'action n'est pas lancee.
     *
     * @param actor      destinataire du message d'echec
     * @param playerId   joueur
     * @param entityType type de banque impose (commande d'un hote : {@code /is bank} = {@code island})
     * @param command    commande tapee ({@code ob bank}), pour la preference de l'hote
     * @param then       action a executer une fois la banque active
     */
    public void selectThen(CommandActor actor, UUID playerId, String entityType, String command, Runnable then) {
        service.select(playerId, entityType, command, result -> {
            if (result.status() == QueryStatus.OK) {
                then.run();
            } else {
                actor.send(messages.get().select(result, entityType, entityTypes.get()));
            }
        });
    }

    @Override
    public void open(CommandActor actor, UUID playerId) {
        menus.accept(playerId);
    }

    @Override
    public void top(CommandActor actor, UUID playerId, String entityType) {
        leaderboard.show(actor, playerId, entityType);
    }
}
