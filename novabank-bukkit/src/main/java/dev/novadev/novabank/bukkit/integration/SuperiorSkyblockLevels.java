package dev.novadev.novabank.bukkit.integration;

import dev.novadev.novabank.config.model.IslandLevelSettings.Target;
import dev.novadev.novabank.core.text.ConsoleText;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Bonus des iles de SuperiorSkyblock2, par reflexion. Signatures VERIFIEES avec SuperiorSkyblock2 2026.3 et 2025.2.1 (javap) :
 * {@code Island.get/setBonusWorth(BigDecimal)}, {@code Island.get/setBonusLevel(BigDecimal)},
 * {@code Island.getPersistentDataContainer()} ({@code has}, {@code get}, {@code put} avec
 * {@code PersistentDataType.BIG_DECIMAL}). La part de NovaBank est retenue dans les donnees persistantes de l'ile.
 */
public final class SuperiorSkyblockLevels {

    private static final String API = "com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI";
    private static final String DATA_TYPE = "com.bgsoftware.superiorskyblock.api.persistence.PersistentDataType";

    private final Class<?> api;
    private final Object bigDecimalType;

    private SuperiorSkyblockLevels(Class<?> api, Object bigDecimalType) {
        this.api = api;
        this.bigDecimalType = bigDecimalType;
    }

    /**
     * @param loader chargeur de classes de SuperiorSkyblock2
     * @return l'acces aux bonus, vide si l'API n'est pas reconnue
     */
    public static Optional<SuperiorSkyblockLevels> create(ClassLoader loader) {
        Optional<Class<?>> api = Reflect.type(loader, API);
        Optional<Class<?>> dataType = Reflect.type(loader, DATA_TYPE);
        if (api.isEmpty() || dataType.isEmpty()) {
            return Optional.empty();
        }
        try {
            Field field = dataType.get().getField("BIG_DECIMAL");
            return Optional.of(new SuperiorSkyblockLevels(api.get(), field.get(null)));
        } catch (ReflectiveOperationException | RuntimeException absent) {
            return Optional.empty();
        }
    }

    /**
     * @param islandId identifiant de l'ile (UUID)
     * @return ses bonus, vide si l'ile n'existe pas
     */
    public Optional<IslandLevelSync.IslandBonus> island(String islandId) {
        try {
            Optional<Object> island = Reflect.call(null, api, "getGrid")
                    .flatMap(grid -> Reflect.call(grid, "getIslandByUUID", UUID.fromString(islandId)));
            return island.map(Bonus::new);
        } catch (IllegalArgumentException notAUuid) {
            return Optional.empty();
        }
    }

    /** @return les identifiants de toutes les iles ({@code GridManager.getIslands()}, verifie sur 2026.3) */
    public java.util.List<String> islandIds() {
        Object islands = Reflect.call(null, api, "getGrid").flatMap(grid -> Reflect.call(grid, "getIslands")).orElse(null);
        java.util.List<String> ids = new java.util.ArrayList<>();
        if (islands instanceof java.util.Collection<?> all) {
            for (Object island : all) {
                Reflect.call(island, "getUniqueId").map(Object::toString).ifPresent(ids::add);
            }
        }
        return ids;
    }

    private final class Bonus implements IslandLevelSync.IslandBonus {

        private final Object island;

        Bonus(Object island) {
            this.island = island;
        }

        private static String key(Target target) {
            return "novabank_applied_" + target.name().toLowerCase(java.util.Locale.ROOT);
        }

        private Optional<Object> container() {
            return Reflect.call(island, "getPersistentDataContainer");
        }

        @Override
        public BigDecimal applied(Target target) {
            return container().filter(data -> Reflect.call(data, "has", key(target)).map(Boolean.TRUE::equals).orElse(false))
                    .flatMap(data -> Reflect.call(data, "get", key(target), bigDecimalType))
                    .filter(BigDecimal.class::isInstance).map(BigDecimal.class::cast).orElse(BigDecimal.ZERO);
        }

        @Override
        public BigDecimal bonus(Target target) {
            return Reflect.call(island, target == Target.WORTH ? "getBonusWorth" : "getBonusLevel")
                    .filter(BigDecimal.class::isInstance).map(BigDecimal.class::cast).orElse(BigDecimal.ZERO);
        }

        @Override
        public void update(Target target, BigDecimal newBonus, BigDecimal newApplied) {
            // La part retenue d'abord : si l'ecriture du bonus echoue ensuite, la prochaine synchronisation ne double rien.
            container().orElseThrow(() -> new IllegalStateException(
                    ConsoleText.tr("donnees persistantes de l'ile indisponibles")));
            Reflect.call(container().get(), "put", key(target), bigDecimalType, newApplied);
            Reflect.call(island, target == Target.WORTH ? "setBonusWorth" : "setBonusLevel", newBonus);
        }
    }
}
