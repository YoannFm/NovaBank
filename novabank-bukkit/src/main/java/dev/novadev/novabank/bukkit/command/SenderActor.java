package dev.novadev.novabank.bukkit.command;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

/**
 * {@link CommandActor} adosse a un emetteur Bukkit.
 *
 * @param sender emetteur
 */
public record SenderActor(CommandSender sender) implements CommandActor {

    /**
     * @throws NullPointerException si l'emetteur est nul
     */
    public SenderActor {
        Objects.requireNonNull(sender, "sender");
    }

    @Override
    public boolean hasPermission(String permission) {
        return sender.hasPermission(permission);
    }

    @Override
    public boolean isConsole() {
        return sender instanceof ConsoleCommandSender;
    }

    @Override
    public void send(String message) {
        for (String line : message.split("\n")) {
            sender.sendMessage(line);
        }
    }

    @Override
    public void sendSuggestion(String message, String command, String hover) {
        sendInteractive(message, new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command), hover);
    }

    @Override
    public void sendLink(String message, String url, String hover) {
        sendInteractive(message, new ClickEvent(ClickEvent.Action.OPEN_URL, url), hover);
    }

    private void sendInteractive(String message, ClickEvent click, String hover) {
        if (!(sender instanceof Player player)) {
            send(message);
            return;
        }
        TextComponent line = new TextComponent(TextComponent.fromLegacyText(message));
        line.setClickEvent(click);
        line.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(new BaseComponent[]{
                new TextComponent(TextComponent.fromLegacyText(hover))})));
        player.spigot().sendMessage(line);
    }

    @Override
    public Optional<UUID> playerId() {
        return sender instanceof Player player ? Optional.of(player.getUniqueId()) : Optional.empty();
    }

    @Override
    public String name() {
        return sender.getName();
    }
}
