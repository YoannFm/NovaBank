package dev.novadev.novabank.bukkit.integration;

import dev.novadev.novabank.bukkit.command.CommandRouter;
import dev.novadev.novabank.bukkit.command.SenderActor;
import dev.novadev.novabank.config.model.CommandLabel;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

/**
 * Sous-commande de banque d'un hote qui accepte un {@link org.bukkit.command.CommandExecutor} Bukkit (Towny :
 * {@code /town bank}). Le nom de la sous-commande est retire des arguments s'il y est encore.
 */
final class HostExecutor implements TabExecutor {

    private final CommandLabel label;
    private final String parentLabel;
    private final CommandRouter router;

    /**
     * @param label       nom et alias ({@code bank})
     * @param parentLabel commande de l'hote, pour l'aide ({@code town})
     * @param router      commande de banque limitee au type de l'hote
     */
    HostExecutor(CommandLabel label, String parentLabel, CommandRouter router) {
        this.label = Objects.requireNonNull(label, "label");
        this.parentLabel = Objects.requireNonNull(parentLabel, "parentLabel");
        this.router = Objects.requireNonNull(router, "router");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        router.execute(new SenderActor(sender), parentLabel + " " + label.name(), withoutName(args));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return router.complete(new SenderActor(sender), withoutName(args));
    }

    String[] withoutName(String[] args) {
        boolean named = args.length > 0 && (label.name().equalsIgnoreCase(args[0])
                || label.aliases().stream().anyMatch(args[0]::equalsIgnoreCase));
        return named ? Arrays.copyOfRange(args, 1, args.length) : args;
    }
}
