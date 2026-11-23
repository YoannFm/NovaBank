package dev.novadev.novabank.bukkit.integration;

import dev.novadev.novabank.bukkit.command.CommandRouter;
import dev.novadev.novabank.bukkit.command.SenderActor;
import dev.novadev.novabank.config.model.CommandLabel;
import dev.novadev.novabank.core.access.Permissions;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.bukkit.command.CommandSender;

/**
 * Sous-commande de banque d'un hote dont la commande se declare par une interface (SuperiorSkyblock2 :
 * {@code SuperiorCommand}). Implementee par un {@link Proxy} sur l'interface de l'hote, chargee par son chargeur : aucune
 * dependance de compilation. Les methodes sont reconnues a leur nom ; {@code args[0]} est le nom tape de la
 * sous-commande, comme chez SuperiorSkyblock2.
 */
final class HostSubcommand implements InvocationHandler {

    /** Sous-commande la plus longue : {@code bank withdraw stone 5}, {@code bank top island minerais}. */
    static final int MAX_ARGS = 4;

    private final CommandLabel label;
    private final String parentLabel;
    private final CommandRouter router;
    private final String description;

    /**
     * @param label       nom et alias de la sous-commande ({@code bank})
     * @param parentLabel commande de l'hote, pour l'aide ({@code is})
     * @param router      commande de banque limitee au type de l'hote
     * @param description description dans l'aide de l'hote
     */
    HostSubcommand(CommandLabel label, String parentLabel, CommandRouter router, String description) {
        this.label = Objects.requireNonNull(label, "label");
        this.parentLabel = Objects.requireNonNull(parentLabel, "parentLabel");
        this.router = Objects.requireNonNull(router, "router");
        this.description = Objects.requireNonNull(description, "description");
    }

    /**
     * @param commandType interface de commande de l'hote
     * @return la sous-commande, instance de {@code commandType}
     */
    Object proxy(Class<?> commandType) {
        return Proxy.newProxyInstance(commandType.getClassLoader(), new Class<?>[] {commandType}, this);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        return switch (method.getName()) {
            case "getAliases" -> aliases();
            case "getPermission" -> Permissions.USE;
            case "getUsage" -> label.name() + " [deposit|withdraw|info|top|open|help]";
            case "getDescription" -> description;
            case "getMinArgs" -> 1;
            case "getMaxArgs" -> MAX_ARGS;
            case "canBeExecutedByConsole" -> false;
            case "displayCommand" -> true;
            case "execute" -> {
                String[] typed = (String[]) args[2];
                router.execute(new SenderActor((CommandSender) args[1]), parentLabel + " " + typed[0], tail(typed));
                yield null;
            }
            case "tabComplete" -> router.complete(new SenderActor((CommandSender) args[1]), tail((String[]) args[2]));
            case "equals" -> proxy == args[0];
            case "hashCode" -> System.identityHashCode(proxy);
            case "toString" -> "NovaBank /" + parentLabel + " " + label.name();
            default -> throw new UnsupportedOperationException(method.getName());
        };
    }

    List<String> aliases() {
        List<String> aliases = new ArrayList<>();
        aliases.add(label.name());
        aliases.addAll(label.aliases());
        return aliases;
    }

    private static String[] tail(String[] typed) {
        return typed.length == 0 ? typed : Arrays.copyOfRange(typed, 1, typed.length);
    }
}
