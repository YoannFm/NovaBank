package dev.novadev.novabank.bukkit.command;

import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.core.application.Amount;
import dev.novadev.novabank.core.application.ItemChoice;
import java.util.Objects;
import java.util.UUID;

/**
 * Actions des joueurs limitees a un type de banque, pour la commande montee dans un hote ({@code /is bank} : banque
 * d'ile). Chaque action rend d'abord active la banque de ce type, puis delegue : la GUI et les commandes {@code /bank}
 * voient donc ensuite la meme banque.
 */
public final class ScopedPlayerActions implements PlayerActions {

    /** Selection silencieuse de la banque d'un type avant une action. */
    @FunctionalInterface
    public interface Selector {

        /**
         * @param actor      destinataire du message d'echec
         * @param playerId   joueur
         * @param entityType type impose
         * @param command    commande tapee ({@code ob bank}), pour la preference de l'hote
         * @param then       action a lancer si la banque est active
         */
        void selectThen(CommandActor actor, UUID playerId, String entityType, String command, Runnable then);
    }

    private final PlayerActions delegate;
    private final Selector selector;
    private final String entityType;
    private final String command;

    /**
     * @param delegate   actions de la commande {@code /bank}
     * @param selector   selection silencieuse
     * @param entityType type de banque impose
     * @param command    commande tapee ({@code ob bank}), vide si inconnue
     */
    public ScopedPlayerActions(PlayerActions delegate, Selector selector, String entityType, String command) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.selector = Objects.requireNonNull(selector, "selector");
        this.entityType = Objects.requireNonNull(entityType, "entityType");
        this.command = Objects.requireNonNull(command, "command");
    }

    @Override
    public void deposit(CommandActor actor, UUID playerId, ItemChoice choice, Amount amount) {
        selector.selectThen(actor, playerId, entityType, command, () -> delegate.deposit(actor, playerId, choice, amount));
    }

    @Override
    public void withdraw(CommandActor actor, UUID playerId, ItemKey item, Amount amount) {
        selector.selectThen(actor, playerId, entityType, command, () -> delegate.withdraw(actor, playerId, item, amount));
    }

    @Override
    public void info(CommandActor actor, UUID playerId, String label) {
        selector.selectThen(actor, playerId, entityType, command, () -> delegate.info(actor, playerId, label));
    }

    /** Le type est impose : le choix demande est ignore. */
    @Override
    public void select(CommandActor actor, UUID playerId, String ignored) {
        delegate.select(actor, playerId, entityType);
    }

    @Override
    public void open(CommandActor actor, UUID playerId) {
        selector.selectThen(actor, playerId, entityType, command, () -> delegate.open(actor, playerId));
    }

    @Override
    public void top(CommandActor actor, UUID playerId, String type) {
        delegate.top(actor, playerId, type == null ? entityType : type);
    }
}
