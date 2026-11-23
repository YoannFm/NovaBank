package dev.novadev.novabank.bukkit.command;

import dev.novadev.novabank.core.application.TaskScheduler;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;

/**
 * Recherche d'un joueur : connecte (nom sans casse), UUID saisi, sinon parmi les joueurs deja
 * venus sur ce serveur.
 *
 * <p>Joueurs connectes : parcours de la liste des joueurs en ligne, sure depuis n'importe
 * quel thread sous Folia (la console y execute ses commandes sur la region globale), et non
 * {@code Server#getPlayerExact}, qui lit une table non synchronisee. Joueurs deja venus : ce
 * parcours lit les donnees de joueurs du monde, il est fait sur l'executeur IO, jamais sur un
 * thread du serveur, et ne declenche aucune requete reseau (contrairement a
 * {@code Server#getOfflinePlayer(String)}).
 */
public final class BukkitPlayerLookup implements PlayerLookup {

    private final Server server;
    private final TaskScheduler scheduler;

    /**
     * @param server    serveur
     * @param scheduler planificateur
     */
    public BukkitPlayerLookup(Server server, TaskScheduler scheduler) {
        this.server = Objects.requireNonNull(server, "server");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public void find(String name, Consumer<Optional<KnownPlayer>> done) {
        for (Player online : server.getOnlinePlayers()) {
            if (online.getName().equalsIgnoreCase(name)) {
                done.accept(Optional.of(new KnownPlayer(online.getUniqueId(), online.getName())));
                return;
            }
        }
        Optional<UUID> uuid = parseUuid(name);
        if (uuid.isPresent()) {
            done.accept(Optional.of(new KnownPlayer(uuid.get(), name)));
            return;
        }
        try {
            scheduler.runAsync(() -> {
                Optional<KnownPlayer> found = Optional.empty();
                for (OfflinePlayer player : server.getOfflinePlayers()) {
                    if (name.equalsIgnoreCase(player.getName())) {
                        found = Optional.of(new KnownPlayer(player.getUniqueId(), player.getName()));
                        break;
                    }
                }
                Optional<KnownPlayer> result = found;
                scheduler.runGlobal(() -> done.accept(result));
            });
        } catch (RejectedExecutionException saturated) {
            done.accept(Optional.empty());
        }
    }

    private static Optional<UUID> parseUuid(String text) {
        try {
            return text.length() == 36 ? Optional.of(UUID.fromString(text)) : Optional.empty();
        } catch (IllegalArgumentException notAUuid) {
            return Optional.empty();
        }
    }
}
