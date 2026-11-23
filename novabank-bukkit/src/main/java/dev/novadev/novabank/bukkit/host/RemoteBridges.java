package dev.novadev.novabank.bukkit.host;

import dev.novadev.novabank.api.operation.OperationType;
import dev.novadev.novabank.bukkit.command.CommandRouter;
import dev.novadev.novabank.bukkit.command.SenderActor;
import dev.novadev.novabank.bukkit.integration.HostBridge;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;

/**
 * Pont « a distance » : quand la commande d'un hote doit etre une sous-classe compilee contre lui (BentoBox :
 * {@code CompositeCommand}), elle vit dans le JAR de l'edition, qui ne fait pas forcement tourner le moteur. Les deux
 * cotes n'echangent alors que des types communs a tous les JAR : le moteur publie la commande de banque de l'edition
 * comme {@link TabExecutor} Bukkit ; le JAR de l'edition publie les droits de l'hote comme {@link Function} du JDK.
 * Chaque service est reconnu a son texte ({@code novabank:commands:<edition>}, {@code novabank:rights:<edition>}).
 */
public final class RemoteBridges {

    static final String COMMANDS = "novabank:commands:";
    static final String RIGHTS = "novabank:rights:";

    private RemoteBridges() {
    }

    /**
     * Cote moteur : publie la commande de banque d'une edition.
     *
     * @param services services du serveur
     * @param owner    plugin proprietaire du moteur
     * @param edition  edition ({@code bentobox})
     * @param router   commande de banque limitee au type de l'hote
     * @return le retrait du service
     */
    static AutoCloseable publishCommands(ServicesManager services, Plugin owner, String edition, CommandRouter router) {
        TabExecutor executor = new PublishedCommands(COMMANDS + edition, router);
        services.register(TabExecutor.class, executor, owner, ServicePriority.Lowest);
        return () -> services.unregister(TabExecutor.class, executor);
    }

    /**
     * Cote edition : commande de banque publiee par le moteur, lue a chaque usage (le moteur peut demarrer apres).
     *
     * @param services services du serveur
     * @param edition  edition
     * @return la commande, ou vide si aucun moteur ne l'a publiee
     */
    public static Optional<TabExecutor> commands(ServicesManager services, String edition) {
        return find(services, TabExecutor.class, COMMANDS + edition);
    }

    /**
     * Cote edition : publie les droits decides par l'hote.
     *
     * @param services services du serveur
     * @param owner    plugin proprietaire (l'hote)
     * @param edition  edition
     * @param rights   decision pour {@code "<uuid du joueur>;<id de l'entite>;DEPOSIT|WITHDRAW"}, ou vide
     * @return le retrait du service
     */
    public static AutoCloseable publishRights(ServicesManager services, Plugin owner, String edition,
                                              Function<String, Optional<Boolean>> rights) {
        Function<String, Optional<Boolean>> published = new PublishedRights(RIGHTS + edition, rights);
        services.register(Function.class, published, owner, ServicePriority.Lowest);
        return () -> services.unregister(Function.class, published);
    }

    /**
     * Cote moteur : droits publies par le JAR d'une edition, lus a chaque operation.
     *
     * @param services services du serveur
     * @param edition  edition
     * @return les droits (aucun tant que rien n'est publie)
     */
    @SuppressWarnings("unchecked")
    static HostBridge.Rights rights(ServicesManager services, String edition) {
        return (playerId, entity, type) -> find(services, Function.class, RIGHTS + edition)
                .flatMap(function -> (Optional<Boolean>) function.apply(query(playerId.toString(), entity.id(), type)));
    }

    /**
     * @param playerId joueur
     * @param entityId identifiant de l'entite
     * @param type     operation
     * @return la requete de droits, au format de {@link #publishRights}
     */
    public static String query(String playerId, String entityId, OperationType type) {
        return playerId + ";" + entityId + ";" + type.name();
    }

    private static <T> Optional<T> find(ServicesManager services, Class<T> type, String text) {
        for (RegisteredServiceProvider<T> registration : services.getRegistrations(type)) {
            if (text.equals(String.valueOf(registration.getProvider()))) {
                return Optional.of(registration.getProvider());
            }
        }
        return Optional.empty();
    }

    /** Commande publiee : {@code label} = ce que le joueur a tape ({@code is bank}), pour l'aide. */
    private record PublishedCommands(String text, CommandRouter router) implements TabExecutor {

        PublishedCommands {
            Objects.requireNonNull(router, "router");
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            router.execute(new SenderActor(sender), label, args);
            return true;
        }

        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
            return router.complete(new SenderActor(sender), args);
        }

        @Override
        public String toString() {
            return text;
        }
    }

    /** Droits publies, reconnus a leur texte. */
    private record PublishedRights(String text, Function<String, Optional<Boolean>> rights)
            implements Function<String, Optional<Boolean>> {

        @Override
        public Optional<Boolean> apply(String query) {
            return rights.apply(query);
        }

        @Override
        public String toString() {
            return text;
        }
    }
}
