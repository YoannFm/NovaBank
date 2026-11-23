package dev.novadev.novabank.bukkit.command;

import dev.novadev.novabank.core.text.ConsoleText;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;

/**
 * Seul point d'acces par reflexion au CommandMap du serveur (absent de spigot-api 1.17.1).
 *
 * <p>Obtention : methode publique {@code getCommandMap()} de l'implementation du serveur.
 * Sur Paper, c'est la methode d'API {@code Server#getCommandMap()} ; sur Spigot/CraftBukkit,
 * la methode publique de {@code CraftServer}. Un seul appel reflexif couvre les deux.
 *
 * <p>Desinscription : {@code getKnownCommands()} si l'implementation l'expose (Paper),
 * sinon le champ {@code knownCommands} de {@code SimpleCommandMap} (Spigot).
 */
public final class CommandMapAccess {

    private CommandMapAccess() {
    }

    /**
     * @param server serveur
     * @return le CommandMap du serveur
     * @throws IllegalStateException si l'implementation n'expose pas de CommandMap
     */
    public static CommandMap commandMap(Server server) {
        Objects.requireNonNull(server, "server");
        try {
            Method getter = server.getClass().getMethod("getCommandMap");
            return (CommandMap) getter.invoke(server);
        } catch (InvocationTargetException failure) {
            throw new IllegalStateException(ConsoleText.tr("CommandMap inaccessible : {0}",
                    failure.getCause()), failure.getCause());
        } catch (ReflectiveOperationException | ClassCastException failure) {
            throw new IllegalStateException(ConsoleText.tr("CommandMap inaccessible sur {0} : {1}",
                    server.getClass().getName(), failure), failure);
        }
    }

    /**
     * Retire une commande (nom, alias et formes prefixees) du CommandMap.
     *
     * @param map     CommandMap
     * @param command commande a retirer
     * @return nombre d'entrees retirees
     * @throws IllegalStateException si la table des commandes est inaccessible
     */
    public static int unregister(CommandMap map, Command command) {
        command.unregister(map);
        Map<String, Command> known = knownCommands(map);
        int before = known.size();
        known.values().removeIf(registered -> registered == command);
        return before - known.size();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Command> knownCommands(CommandMap map) {
        try {
            Method getter = map.getClass().getMethod("getKnownCommands");
            return (Map<String, Command>) getter.invoke(map);
        } catch (NoSuchMethodException absent) {
            return (Map<String, Command>) readField(map);
        } catch (ReflectiveOperationException | ClassCastException failure) {
            throw new IllegalStateException(ConsoleText.tr("Table des commandes inaccessible : {0}", failure), failure);
        }
    }

    private static Object readField(CommandMap map) {
        for (Class<?> type = map.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField("knownCommands");
                field.setAccessible(true);
                return field.get(map);
            } catch (NoSuchFieldException notHere) {
                // Champ declare plus haut dans la hierarchie (SimpleCommandMap).
            } catch (IllegalAccessException | RuntimeException failure) {
                throw new IllegalStateException(ConsoleText.tr("Table des commandes inaccessible : {0}",
                        failure), failure);
            }
        }
        throw new IllegalStateException(ConsoleText.tr("Table des commandes introuvable dans {0}",
                map.getClass().getName()));
    }
}
