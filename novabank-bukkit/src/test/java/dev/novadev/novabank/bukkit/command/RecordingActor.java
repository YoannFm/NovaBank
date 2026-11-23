package dev.novadev.novabank.bukkit.command;

import dev.novadev.novabank.core.access.Permissions;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.bukkit.ChatColor;

/**
 * Emetteur de commande factice : joueur ou console, permissions choisies, messages
 * enregistres sans couleur.
 */
final class RecordingActor implements CommandActor {

    private final boolean console;
    private final UUID player;
    private final Set<String> permissions = new HashSet<>();
    private final List<String> received = new CopyOnWriteArrayList<>();
    private final List<String> suggestions = new CopyOnWriteArrayList<>();
    private final List<String> links = new CopyOnWriteArrayList<>();

    RecordingActor(boolean admin) {
        this(admin, false);
    }

    RecordingActor(boolean admin, boolean console) {
        this.console = console;
        this.player = console ? null : UUID.fromString("00000000-0000-0000-0000-0000000000aa");
        if (admin) {
            permissions.add(Permissions.ADMIN);
            permissions.add(Permissions.ADMIN_RESET);
            permissions.add(Permissions.ADMIN_MIGRATE);
        }
        permissions.add(Permissions.USE);
    }

    List<String> suggestions() {
        return suggestions;
    }

    List<String> links() {
        return links;
    }

    @Override
    public void sendSuggestion(String message, String command, String hover) {
        suggestions.add(command);
        send(message);
    }

    @Override
    public void sendLink(String message, String url, String hover) {
        links.add(url);
        send(message);
    }

    RecordingActor grant(String permission) {
        permissions.add(permission);
        return this;
    }

    RecordingActor deny(String permission) {
        permissions.remove(permission);
        return this;
    }

    @Override
    public boolean isConsole() {
        return console;
    }

    @Override
    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }

    @Override
    public void send(String message) {
        for (String line : message.split("\n")) {
            received.add(ChatColor.stripColor(line));
        }
    }

    @Override
    public Optional<UUID> playerId() {
        return Optional.ofNullable(player);
    }

    @Override
    public String name() {
        return console ? "CONSOLE" : "Admin";
    }

    UUID id() {
        return player;
    }

    List<String> received() {
        return received;
    }
}
