package dev.novadev.novabank.bukkit.listener;

import dev.novadev.novabank.bukkit.command.KnownStock;
import dev.novadev.novabank.bukkit.command.TransferMessages;
import dev.novadev.novabank.core.application.BankService;
import dev.novadev.novabank.core.application.TaskScheduler;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Connexion : remise des livraisons en attente, apres un court delai (joueur entierement
 * charge). Deconnexion : oubli du stock connu. La fermeture desinscrit l'ecouteur.
 */
public final class PlayerConnectionListener implements Listener, AutoCloseable {

    /** Delai entre la connexion et la remise des livraisons. */
    static final Duration DELIVERY_DELAY = Duration.ofSeconds(2);

    private final BankService service;
    private final TaskScheduler scheduler;
    private final Supplier<TransferMessages> messages;
    private final KnownStock stock;
    private final Function<UUID, Player> players;

    /**
     * @param service   operations des joueurs
     * @param scheduler planificateur
     * @param messages  textes des resultats
     * @param stock     stock connu (completion)
     * @param players   joueur connecte par UUID
     */
    public PlayerConnectionListener(BankService service, TaskScheduler scheduler, Supplier<TransferMessages> messages,
                                    KnownStock stock, Function<UUID, Player> players) {
        this.service = Objects.requireNonNull(service, "service");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.stock = Objects.requireNonNull(stock, "stock");
        this.players = Objects.requireNonNull(players, "players");
    }

    /**
     * @param event connexion d'un joueur
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        scheduler.runLaterGlobal(() -> service.deliverPending(playerId, report -> {
            Player player = players.apply(playerId);
            if (player != null) {
                messages.get().delivery(report).forEach(player::sendMessage);
            }
        }), DELIVERY_DELAY);
    }

    /**
     * @param event deconnexion d'un joueur
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        stock.forget(event.getPlayer().getUniqueId());
    }

    @Override
    public void close() {
        HandlerList.unregisterAll(this);
    }
}
