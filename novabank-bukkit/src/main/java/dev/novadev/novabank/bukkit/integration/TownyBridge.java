package dev.novadev.novabank.bukkit.integration;

import dev.novadev.novabank.core.text.ConsoleText;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;

/**
 * Pont Towny : sous-commande {@code /town <nom>} (et ses alias) par l'API officielle d'ajout de commandes, et droits par
 * permissions attribuees aux rangs dans {@code townyperms.yml} ({@code novabank.town.deposit},
 * {@code novabank.town.withdraw}) ; sans elles, maire = proprietaire et assistant = admin (regle banks.roles).
 *
 * <p>API VERIFIEE avec Towny 0.103.2.0 (javap) : {@code AddonCommand(CommandType, String, CommandExecutor)},
 * {@code setTabCompleter(TabCompleter)}, {@code TownyCommandAddonAPI.addSubCommand(AddonCommand)} et
 * {@code removeSubCommand(AddonCommand)} ; l'argument du nom est retire par Towny avant l'appel.
 */
final class TownyBridge implements HostBridge {

    private static final String API = "com.palmergames.bukkit.towny.TownyCommandAddonAPI";
    private static final String ADDON = "com.palmergames.bukkit.towny.object.AddonCommand";

    @Override
    public Mount mount(Context context) throws ReflectiveOperationException {
        ClassLoader loader = context.host().getClass().getClassLoader();
        Class<?> api = Class.forName(API, true, loader);
        Class<?> addon = Class.forName(ADDON, true, loader);
        Class<?> types = Class.forName(API + "$CommandType", true, loader);
        Object town = types.getField("TOWN").get(null);
        HostExecutor executor = new HostExecutor(context.label(), "town", context.commands());

        List<Object> registered = new ArrayList<>();
        List<String> names = new ArrayList<>(List.of(context.label().name()));
        names.addAll(context.label().aliases());
        for (String name : names) {
            Object command = addon.getConstructor(types, String.class, CommandExecutor.class).newInstance(town, name,
                    executor);
            addon.getMethod("setTabCompleter", TabCompleter.class).invoke(command, executor);
            if (Boolean.TRUE.equals(api.getMethod("addSubCommand", addon).invoke(null, command))) {
                registered.add(command);
            } else {
                context.logger().warning(ConsoleText.tr("Towny : /town {0} existe deja, alias ignore.", name));
            }
        }
        return new Mount(() -> {
            for (Object command : registered) {
                api.getMethod("removeSubCommand", addon).invoke(null, command);
            }
        }, new PermissionRights(context.entityType(), context.host().getServer()::getPlayer),
                ConsoleText.tr("/town {0}, permissions novabank.town.deposit|withdraw", context.label().name()));
    }
}
