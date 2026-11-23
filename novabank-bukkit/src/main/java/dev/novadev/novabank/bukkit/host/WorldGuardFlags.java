package dev.novadev.novabank.bukkit.host;

import dev.novadev.novabank.api.operation.OperationType;
import dev.novadev.novabank.bukkit.integration.Reflect;
import java.util.List;
import java.util.Optional;
import org.bukkit.plugin.Plugin;

/**
 * NovaBank pour WorldGuard : flags de region {@code novabank-deposit} et {@code novabank-withdraw} ({@code allow} ou
 * {@code deny}, par {@code /rg flag <region> novabank-withdraw allow}). Un flag non pose ne decide rien : proprietaires
 * de la region retirent, membres deposent (regle banks.roles). Livree seulement dans le JAR NovaBank-WorldGuard
 * (fichier de service ajoute par novabank-editions). Par reflexion : WorldGuard 7.0.17 est en Java 21 et WorldEdit
 * 7.4.6 en Java 25.
 *
 * <p>API VERIFIEE avec WorldGuard 7.0.17 (javap) : {@code WorldGuard.getInstance().getFlagRegistry().register(Flag)}
 * et {@code get(String)}, {@code StateFlag(String, boolean)}, {@code getPlatform().getRegionContainer().getLoaded()},
 * {@code RegionManager.getName()} et {@code getRegion(String)}, {@code ProtectedRegion.getFlag(Flag)}.
 */
public final class WorldGuardFlags implements EditionExtension {

    static final String EDITION = "worldguard";
    static final String DEPOSIT = "novabank-deposit";
    static final String WITHDRAW = "novabank-withdraw";
    private static final String WORLDGUARD = "com.sk89q.worldguard.WorldGuard";

    /** WorldGuard n'accepte de nouveaux flags qu'avant son activation : au chargement du plugin. */
    @Override
    public void load(Plugin plugin) throws ClassNotFoundException {
        ClassLoader loader = worldGuardLoader(plugin);
        Class<?> stateFlag = Reflect.type(loader, "com.sk89q.worldguard.protection.flags.StateFlag")
                .orElseThrow(() -> new ClassNotFoundException("StateFlag"));
        Optional<Object> registry = registry(loader);
        for (String name : List.of(DEPOSIT, WITHDRAW)) {
            if (registry.flatMap(found -> Reflect.call(found, "get", name)).isEmpty()) {
                Reflect.construct(stateFlag, name, false)
                        .ifPresent(flag -> registry.ifPresent(found -> Reflect.call(found, "register", flag)));
            }
        }
    }

    @Override
    public AutoCloseable start(Plugin plugin) {
        ClassLoader loader = worldGuardLoader(plugin);
        return RemoteBridges.publishRights(plugin.getServer().getServicesManager(), plugin, EDITION,
                query -> allows(loader, query));
    }

    private static ClassLoader worldGuardLoader(Plugin plugin) {
        Plugin worldGuard = plugin.getServer().getPluginManager().getPlugin("WorldGuard");
        return worldGuard == null ? plugin.getClass().getClassLoader() : worldGuard.getClass().getClassLoader();
    }

    private static Optional<Object> registry(ClassLoader loader) {
        return Reflect.type(loader, WORLDGUARD).flatMap(type -> Reflect.call(null, type, "getInstance"))
                .flatMap(worldGuard -> Reflect.call(worldGuard, "getFlagRegistry"));
    }

    /** Requete {@code "<joueur>;<monde>:<region>;DEPOSIT|WITHDRAW"} : valeur du flag pose sur la region. */
    private static Optional<Boolean> allows(ClassLoader loader, String query) {
        String[] parts = query.split(";", 3);
        int colon = parts.length == 3 ? parts[1].indexOf(':') : -1;
        if (colon < 0) {
            return Optional.empty();
        }
        String world = parts[1].substring(0, colon);
        String regionId = parts[1].substring(colon + 1);
        Optional<Object> flag = registry(loader).flatMap(found -> Reflect.call(found, "get",
                OperationType.DEPOSIT.name().equals(parts[2]) ? DEPOSIT : WITHDRAW));
        Optional<Object> region = Reflect.type(loader, WORLDGUARD).flatMap(type -> Reflect.call(null, type, "getInstance"))
                .flatMap(worldGuard -> Reflect.call(worldGuard, "getPlatform"))
                .flatMap(platform -> Reflect.call(platform, "getRegionContainer"))
                .flatMap(container -> Reflect.call(container, "getLoaded"))
                .flatMap(managers -> ((List<?>) managers).stream()
                        .filter(manager -> Reflect.call(manager, "getName").map(world::equals).orElse(false))
                        .findFirst())
                .flatMap(manager -> Reflect.call(manager, "getRegion", regionId));
        if (flag.isEmpty() || region.isEmpty()) {
            return Optional.empty();
        }
        return Reflect.call(region.get(), "getFlag", flag.get()).map(state -> "ALLOW".equals(String.valueOf(state)));
    }
}
