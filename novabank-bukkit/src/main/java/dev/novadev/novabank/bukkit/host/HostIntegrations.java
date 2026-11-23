package dev.novadev.novabank.bukkit.host;

import dev.novadev.novabank.api.entity.EntityMembership;
import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.entity.OwnershipProvider;
import dev.novadev.novabank.api.event.ComponentStateEvent;
import dev.novadev.novabank.api.operation.OperationType;
import dev.novadev.novabank.bukkit.command.CommandRouter;
import dev.novadev.novabank.bukkit.integration.HostBridge;
import dev.novadev.novabank.bukkit.integration.OwnershipIntegrations;
import dev.novadev.novabank.bukkit.integration.OwnershipIntegrations.Integration;
import dev.novadev.novabank.bukkit.publicapi.ComponentEvents;
import dev.novadev.novabank.config.model.CommandLabel;
import dev.novadev.novabank.core.ownership.OwnershipRegistry;
import dev.novadev.novabank.core.text.ConsoleText;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.event.server.ServiceUnregisterEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicesManager;

/**
 * Active les integrations des plugins hotes selon les editions NovaBank installees : le fournisseur d'entites de
 * l'hote (edition de l'hote, ou plugin autonome qui les couvre toutes), puis son pont (commande {@code /is bank},
 * droits de l'hote) si l'edition de l'hote est la. Reevalue a chaque plugin active ou desactive et a chaque edition
 * annoncee ou retiree : l'ordre de chargement des JAR et des hotes est donc indifferent. Thread principal.
 */
public final class HostIntegrations implements Listener, AutoCloseable {

    /** Edition du plugin autonome : fournisseurs de tous les hotes, sans pont. */
    public static final String STANDALONE = "standalone";

    /** Commandes de banque fournies par le moteur. */
    public interface Commands {

        /**
         * @param entityType type de banque
         * @return la commande de banque limitee a ce type
         */
        CommandRouter forType(String entityType);

        /**
         * @param label  nom et alias
         * @param router logique
         * @return le retrait de la commande
         */
        AutoCloseable topLevel(CommandLabel label, CommandRouter router);

        /** @return description de la commande de banque, dans la langue du serveur */
        String description();
    }

    private record Active(String entityType, String description, AutoCloseable unmount) {
    }

    private final Plugin owner;
    private final PluginManager plugins;
    private final ServicesManager services;
    private final OwnershipRegistry registry;
    private final Function<UUID, Optional<String>> playerNames;
    private final Logger logger;
    private final List<String> loaded;
    private final Supplier<Map<String, CommandLabel>> labels;
    private final Commands commands;
    private final Map<String, Active> active = new HashMap<>();

    /**
     * @param owner       plugin proprietaire des ecouteurs de NovaBank
     * @param plugins     gestionnaire de plugins
     * @param services    services du serveur (annonces des editions)
     * @param registry    registre des fournisseurs d'entites
     * @param playerNames nom connu d'un joueur
     * @param logger      journal
     * @param loaded      integrations chargees, pour le diagnostic (liste sure entre threads)
     * @param labels      nom et alias de la commande de banque de chaque hote ({@code commands.hosts})
     * @param commands    commandes de banque
     */
    public HostIntegrations(Plugin owner, PluginManager plugins, ServicesManager services, OwnershipRegistry registry,
                            Function<UUID, Optional<String>> playerNames, Logger logger, List<String> loaded,
                            Supplier<Map<String, CommandLabel>> labels, Commands commands) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.plugins = Objects.requireNonNull(plugins, "plugins");
        this.services = Objects.requireNonNull(services, "services");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.playerNames = Objects.requireNonNull(playerNames, "playerNames");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.loaded = Objects.requireNonNull(loaded, "loaded");
        this.labels = Objects.requireNonNull(labels, "labels");
        this.commands = Objects.requireNonNull(commands, "commands");
    }

    /**
     * @param editions editions installees
     * @param edition  edition d'un hote
     * @return {@code true} si le fournisseur d'entites de cet hote est couvert
     */
    static boolean providerLicensed(Set<String> editions, String edition) {
        return editions.contains(STANDALONE) || editions.contains(edition);
    }

    /** Active ou retire les integrations selon les editions annoncees et les plugins actives. */
    public void refresh() {
        Set<String> editions = EditionChannel.editions(services);
        for (Integration integration : OwnershipIntegrations.all()) {
            Plugin host = plugins.getPlugin(integration.pluginName());
            boolean wanted = host != null && host.isEnabled() && providerLicensed(editions, integration.edition());
            if (wanted && !active.containsKey(integration.edition())) {
                activate(integration, host, editions.contains(integration.edition()));
            } else if (!wanted && active.containsKey(integration.edition())) {
                deactivate(integration.edition());
            }
        }
    }

    private void activate(Integration integration, Plugin host, boolean withBridge) {
        Optional<OwnershipProvider> created = integration.factory().apply(new OwnershipIntegrations.Context(
                host.getClass().getClassLoader(), playerNames, owner));
        if (created.isEmpty()) {
            logger.warning(
                    ConsoleText.tr("Integration : {0} est installe mais son API n'est pas reconnue (version non prise en charge).",
                            integration.pluginName()));
            ComponentEvents.fire(plugins, ComponentStateEvent.Kind.INTEGRATION, integration.pluginName(), false,
                    "API non reconnue");
            return;
        }
        OwnershipProvider provider = created.get();
        HostBridge.Mount mount = withBridge ? edition(integration, host, provider.entityType()) : null;
        try {
            registry.register(mount == null ? provider : new HostRightsProvider(provider, mount.rights()));
        } catch (IllegalStateException alreadyHandled) {
            logger.warning(ConsoleText.tr("Integration : {0} ignore, le type '{1}' est deja gere par un autre plugin.",
                    integration.pluginName(), provider.entityType()));
            close(mount);
            return;
        }
        String description = integration.pluginName() + " " + host.getDescription().getVersion() + " -> "
                + provider.entityType() + (mount == null ? "" : " ; " + mount.summary());
        loaded.add(description);
        active.put(integration.edition(), new Active(provider.entityType(), description,
                mount == null ? () -> { } : mount.unmount()));
        logger.info(mount == null ? ConsoleText.tr("Integration : {0} prise en charge (type de banque '{1}')",
                integration.pluginName(), provider.entityType())
                : ConsoleText.tr("Integration : {0} prise en charge (type de banque '{1}'), {2}",
                        integration.pluginName(), provider.entityType(), mount.summary()));
        ComponentEvents.fire(plugins, ComponentStateEvent.Kind.INTEGRATION, integration.pluginName(), true,
                provider.entityType());
    }

    /**
     * Ce qu'apporte l'edition de l'hote : la commande de banque publiee pour un pont a distance (addon BentoBox), et le
     * pont local s'il y en a un ; droits du pont local, a defaut ceux publies par l'extension de l'edition.
     */
    private HostBridge.Mount edition(Integration integration, Plugin host, String entityType) {
        AutoCloseable published = RemoteBridges.publishCommands(services, owner, integration.edition(),
                commands.forType(entityType));
        HostBridge.Mount local = mount(integration, host, entityType);
        HostBridge.Rights remote = RemoteBridges.rights(services, integration.edition());
        HostBridge.Rights rights = local == null ? remote : (playerId, entity, type) -> local.rights()
                .allows(playerId, entity, type).or(() -> remote.allows(playerId, entity, type));
        return new HostBridge.Mount(() -> {
            close(local);
            published.close();
        }, rights, local != null ? local.summary() : ConsoleText.tr("commande publiee pour l'extension {0}",
                integration.edition()));
    }

    private HostBridge.Mount mount(Integration integration, Plugin host, String entityType) {
        HostBridge bridge = integration.bridge();
        CommandLabel label = labels.get().get(integration.edition());
        if (bridge == null || label == null) {
            return null;
        }
        try {
            return bridge.mount(new HostBridge.Context(host, owner, entityType, label, commands.forType(entityType),
                    commands::topLevel, commands.description(), logger));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            logger.log(Level.WARNING,
                    ConsoleText.tr("Integration : commande et droits de {0} indisponibles (API non reconnue) ; la banque reste accessible par /bank.",
                            integration.pluginName()), failure);
            return null;
        }
    }

    private void deactivate(String edition) {
        Active removed = active.remove(edition);
        registry.unregister(removed.entityType());
        loaded.remove(removed.description());
        close(removed.unmount());
        logger.info(ConsoleText.tr("Integration retiree : {0}", removed.description()));
    }

    private void close(HostBridge.Mount mount) {
        if (mount != null) {
            close(mount.unmount());
        }
    }

    private void close(AutoCloseable unmount) {
        try {
            unmount.close();
        } catch (Exception failure) {
            logger.log(Level.WARNING, ConsoleText.tr("Retrait d'une integration incomplet"), failure);
        }
    }

    /** @param event un plugin (peut-etre un hote) vient d'etre active */
    @EventHandler
    public void onEnable(PluginEnableEvent event) {
        refresh();
    }

    /** @param event un plugin (peut-etre un hote) va etre desactive */
    @EventHandler
    public void onDisable(PluginDisableEvent event) {
        if (event.getPlugin() != owner) {
            refresh();
        }
    }

    /** @param event un service vient d'etre enregistre (peut-etre l'annonce d'une edition) */
    @EventHandler
    public void onAnnounce(ServiceRegisterEvent event) {
        if (EditionChannel.isAnnouncement(event.getProvider().getProvider())) {
            refresh();
        }
    }

    /** @param event un service vient d'etre retire */
    @EventHandler
    public void onWithdraw(ServiceUnregisterEvent event) {
        if (EditionChannel.isAnnouncement(event.getProvider().getProvider())) {
            refresh();
        }
    }

    /** Retire toutes les integrations (arret de NovaBank). */
    @Override
    public void close() {
        List.copyOf(active.keySet()).forEach(this::deactivate);
    }

    /** Fournisseur d'un hote dont le pont decide aussi les droits. */
    private record HostRightsProvider(OwnershipProvider delegate, HostBridge.Rights hostRights)
            implements OwnershipProvider {

        @Override
        public String entityType() {
            return delegate.entityType();
        }

        @Override
        public List<EntityMembership> resolve(UUID playerId) {
            return delegate.resolve(playerId);
        }

        @Override
        public Optional<String> displayName(EntityRef entity) {
            return delegate.displayName(entity);
        }

        @Override
        public Optional<Boolean> allows(UUID playerId, EntityRef entity, OperationType type) {
            return hostRights.allows(playerId, entity, type);
        }
    }
}
