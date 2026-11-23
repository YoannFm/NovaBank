package dev.novadev.novabank.bukkit.gui;

import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.bukkit.command.CommandActor;
import dev.novadev.novabank.bukkit.command.PlayerCommandActions;
import dev.novadev.novabank.core.application.Amount;
import dev.novadev.novabank.core.application.BankView;
import dev.novadev.novabank.core.application.ItemChoice;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Operations de la GUI par les actions de la commande des joueurs : memes controles, memes
 * messages, meme mise a jour du stock connu.
 */
public final class CommandMenuBackend implements MenuBackend {

    private final PlayerCommandActions actions;
    private final Function<UUID, CommandActor> actors;

    /**
     * @param actions actions des joueurs
     * @param actors  destinataire des messages d'un joueur connecte
     */
    public CommandMenuBackend(PlayerCommandActions actions, Function<UUID, CommandActor> actors) {
        this.actions = Objects.requireNonNull(actions, "actions");
        this.actors = Objects.requireNonNull(actors, "actors");
    }

    @Override
    public void view(UUID playerId, Consumer<BankView> done) {
        actions.view(playerId, done);
    }

    @Override
    public void withdraw(UUID playerId, ItemKey item, long quantity, Runnable done) {
        actions.withdraw(actors.apply(playerId), playerId, item, Amount.of(quantity), done);
    }

    @Override
    public void depositAll(UUID playerId, Runnable done) {
        actions.deposit(actors.apply(playerId), playerId, ItemChoice.allAccepted(), Amount.everything(), done);
    }
}
