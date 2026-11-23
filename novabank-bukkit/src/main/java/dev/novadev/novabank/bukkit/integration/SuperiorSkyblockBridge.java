package dev.novadev.novabank.bukkit.integration;

import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.operation.OperationType;
import dev.novadev.novabank.core.text.ConsoleText;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Pont SuperiorSkyblock2 : sous-commande {@code /is <nom>} (nom et alias de {@code commands.hosts.superiorskyblock2})
 * et privileges d'ile {@code NOVABANK_DEPOSIT} et {@code NOVABANK_WITHDRAW}, reglables par role dans le menu des
 * permissions de l'ile. Un privilege jamais regle sur une ile ne decide rien : la regle {@code banks.roles} s'applique
 * (par defaut tout membre depose, chef et co-chefs retirent).
 *
 * <p>Signatures VERIFIEES avec SuperiorSkyblock2 2026.3 et 2025.2.1 (javap de l'API publiee par BG-Software) : {@code SuperiorSkyblockAPI.getCommands()},
 * {@code CommandsManager.registerCommand/unregisterCommand(SuperiorCommand)}, {@code IslandPrivilege.register(String)},
 * {@code IslandPrivilege.getByName(String)}, {@code Island.getRolePermissions()} (privilege vers role requis),
 * {@code Island.hasPermission(SuperiorPlayer, IslandPrivilege)}.
 */
final class SuperiorSkyblockBridge implements HostBridge {

    private static final String PACKAGE = "com.bgsoftware.superiorskyblock.api.";
    static final String DEPOSIT = "NOVABANK_DEPOSIT";
    static final String WITHDRAW = "NOVABANK_WITHDRAW";

    @Override
    public Mount mount(Context context) throws ReflectiveOperationException {
        ClassLoader loader = context.host().getClass().getClassLoader();
        Class<?> api = Class.forName(PACKAGE + "SuperiorSkyblockAPI", true, loader);
        Class<?> commandType = Class.forName(PACKAGE + "commands.SuperiorCommand", true, loader);
        Class<?> manager = Class.forName(PACKAGE + "handlers.CommandsManager", true, loader);
        Class<?> privilegeType = Class.forName(PACKAGE + "island.IslandPrivilege", true, loader);

        Object deposit = privilege(privilegeType, DEPOSIT);
        Object withdraw = privilege(privilegeType, WITHDRAW);
        Object command = new HostSubcommand(context.label(), "is", context.commands(), context.description())
                .proxy(commandType);
        Object commands = api.getMethod("getCommands").invoke(null);
        Method register = manager.getMethod("registerCommand", commandType);
        Method unregister = manager.getMethod("unregisterCommand", commandType);
        Method existing = manager.getMethod("getCommand", String.class);
        if (existing.invoke(commands, context.label().name()) != null) {
            context.logger().warning(
                    ConsoleText.tr("SuperiorSkyblock2 a deja /is {0} : changez commands.hosts.superiorskyblock2.name dans plugins/NovaBank/config.yml.",
                            context.label().name()));
        }
        register.invoke(commands, command);
        writeMenuEntries(context);

        Rights rights = (playerId, entity, type) -> decide(api, playerId, entity,
                type == OperationType.DEPOSIT ? deposit : withdraw);
        return new Mount(() -> unregister.invoke(commands, command), rights,
                ConsoleText.tr("/is {0}, privileges {1} et {2}",
                        context.label().name(), DEPOSIT, WITHDRAW));
    }

    /**
     * Ecrit une fois, dans {@code plugins/NovaBank/hosts/}, les entrees du menu des permissions a copier dans
     * SuperiorSkyblock2 (fr et en) ; un fichier deja present n'est jamais remplace.
     */
    private static void writeMenuEntries(Context context) {
        java.nio.file.Path folder = context.host().getDataFolder().toPath()
                .resolveSibling(dev.novadev.novabank.bukkit.lifecycle.PluginHost.DATA_FOLDER).resolve("hosts");
        for (String language : new String[] {"fr", "en"}) {
            String name = "superiorskyblock2-permissions-" + language + ".yml";
            java.nio.file.Path target = folder.resolve(name);
            if (java.nio.file.Files.exists(target)) {
                continue;
            }
            try (java.io.InputStream source = SuperiorSkyblockBridge.class.getResourceAsStream("/hosts/" + name)) {
                if (source == null) {
                    continue;
                }
                java.nio.file.Files.createDirectories(folder);
                java.nio.file.Files.copy(source, target);
                context.logger().info(
                        ConsoleText.tr("SuperiorSkyblock2 : entrees du menu des permissions a copier dans menus/permissions.yml ecrites dans {0}",
                                target));
            } catch (java.io.IOException failure) {
                context.logger().warning(ConsoleText.tr("SuperiorSkyblock2 : {0} non ecrit : {1}", target, failure));
            }
        }
    }

    /** Enregistre le privilege (une seule fois par demarrage de SuperiorSkyblock2), puis le relit. */
    private static Object privilege(Class<?> privilegeType, String name) throws ReflectiveOperationException {
        Method byName = privilegeType.getMethod("getByName", String.class);
        try {
            return byName.invoke(null, name);
        } catch (java.lang.reflect.InvocationTargetException unknown) {
            privilegeType.getMethod("register", String.class).invoke(null, name);
            return byName.invoke(null, name);
        }
    }

    private static Optional<Boolean> decide(Class<?> api, UUID playerId, EntityRef entity, Object privilege) {
        Optional<UUID> islandId = islandId(entity);
        if (islandId.isEmpty()) {
            return Optional.empty();
        }
        Optional<Object> island = Reflect.call(null, api, "getGrid")
                .flatMap(grid -> Reflect.call(grid, "getIslandByUUID", islandId.get()));
        boolean configured = island.flatMap(found -> Reflect.call(found, "getRolePermissions"))
                .map(permissions -> ((Map<?, ?>) permissions).containsKey(privilege)).orElse(false);
        if (!configured) {
            return Optional.empty();
        }
        Optional<Object> player = Reflect.call(null, api, "getPlayer", playerId);
        return player.flatMap(found -> Reflect.call(island.get(), "hasPermission", found, privilege))
                .map(Boolean.class::cast);
    }

    /** Identifiant d'ile illisible (donnee ancienne ou corrompue) : aucune decision, {@code banks.roles} s'applique. */
    static Optional<UUID> islandId(EntityRef entity) {
        try {
            return Optional.of(UUID.fromString(entity.id()));
        } catch (IllegalArgumentException unreadable) {
            return Optional.empty();
        }
    }
}
