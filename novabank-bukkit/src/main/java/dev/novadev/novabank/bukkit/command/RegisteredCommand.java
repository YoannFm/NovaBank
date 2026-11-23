package dev.novadev.novabank.bukkit.command;

import dev.novadev.novabank.core.text.ConsoleText;
import java.util.Objects;
import java.util.logging.Logger;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;

/**
 * Commande inscrite dans le CommandMap ; la fermeture la desinscrit (desactivation,
 * rechargement par un gestionnaire de plugins).
 */
public final class RegisteredCommand implements AutoCloseable {

    private final CommandMap map;
    private final Command command;
    private final Logger logger;

    private RegisteredCommand(CommandMap map, Command command, Logger logger) {
        this.map = map;
        this.command = command;
        this.logger = logger;
    }

    /**
     * Inscrit la commande sous le prefixe donne ({@code prefixe:nom}) et, si le nom est
     * libre, sous son nom court.
     *
     * @param map            CommandMap du serveur
     * @param fallbackPrefix prefixe (nom du plugin en minuscules)
     * @param command        commande
     * @param logger         journal (conflit de nom)
     * @return la commande inscrite
     */
    public static RegisteredCommand register(CommandMap map, String fallbackPrefix, Command command, Logger logger) {
        Objects.requireNonNull(map, "map");
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(logger, "logger");
        if (!map.register(fallbackPrefix, command)) {
            logger.warning(ConsoleText.tr("Le nom /{0} est deja pris par un autre plugin : utilisez /{1}:{2}",
                    command.getName(), fallbackPrefix, command.getName()));
        }
        return new RegisteredCommand(map, command, logger);
    }

    @Override
    public void close() {
        int removed = CommandMapAccess.unregister(map, command);
        logger.fine(ConsoleText.tr("Commande /{0} desinscrite ({1} entree(s))", command.getName(), removed));
    }
}
