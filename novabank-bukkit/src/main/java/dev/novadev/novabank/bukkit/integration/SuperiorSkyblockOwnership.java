package dev.novadev.novabank.bukkit.integration;

import dev.novadev.novabank.api.entity.BankRole;
import dev.novadev.novabank.api.entity.EntityMembership;
import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.entity.OwnershipProvider;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Iles de SuperiorSkyblock2 (type {@code island}, identifiant = UUID de l'ile). Par reflexion, d'apres l'API
 * documentee ; signatures VERIFIEES avec SuperiorSkyblock2 2026.3 (javap) :
 * {@code SuperiorSkyblockAPI.getPlayer(UUID)}, {@code SuperiorPlayer.getIsland()}, {@code SuperiorPlayer.getPlayerRole()}
 * ({@code getName()}), {@code Island.getUniqueId()}, {@code Island.getOwner()}, {@code Island.getName()},
 * {@code SuperiorSkyblockAPI.getGrid().getIslandByUUID(UUID)}.
 *
 * <p>Roles : le chef de l'ile est {@code OWNER} ; un role dont le nom contient LEADER, ADMIN ou MOD (co-chef, admin,
 * moderateur) est {@code ADMIN} ; les autres membres sont {@code MEMBER}.
 */
public final class SuperiorSkyblockOwnership implements OwnershipProvider {

    private static final String API = "com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI";

    private final Class<?> api;
    private final Function<UUID, Optional<String>> playerNames;

    private SuperiorSkyblockOwnership(Class<?> api, Function<UUID, Optional<String>> playerNames) {
        this.api = api;
        this.playerNames = playerNames;
    }

    /**
     * @param loader      chargeur de classes du plugin
     * @param playerNames nom connu d'un joueur
     * @return le fournisseur, vide si l'API n'est pas trouvee
     */
    public static Optional<OwnershipProvider> create(ClassLoader loader, Function<UUID, Optional<String>> playerNames) {
        return Reflect.type(loader, API).map(api -> new SuperiorSkyblockOwnership(api, playerNames));
    }

    @Override
    public String entityType() {
        return EntityRef.ISLAND;
    }

    @Override
    public List<EntityMembership> resolve(UUID playerId) {
        Optional<Object> player = Reflect.call(null, api, "getPlayer", playerId);
        Optional<Object> island = player.flatMap(found -> Reflect.call(found, "getIsland"));
        Optional<String> islandId = island.flatMap(found -> Reflect.call(found, "getUniqueId")).map(Object::toString);
        if (islandId.isEmpty()) {
            return List.of();
        }
        boolean owner = island.flatMap(found -> Reflect.call(found, "getOwner"))
                .flatMap(chief -> Reflect.call(chief, "getUniqueId")).map(playerId::equals).orElse(false);
        BankRole role = owner ? BankRole.OWNER : roleOf(player.get());
        return List.of(new EntityMembership(new EntityRef(EntityRef.ISLAND, islandId.get()), role));
    }

    @Override
    public Optional<String> displayName(EntityRef entity) {
        try {
            Optional<Object> grid = Reflect.call(null, api, "getGrid");
            Optional<Object> island = grid.flatMap(found -> Reflect.call(found, "getIslandByUUID",
                    UUID.fromString(entity.id())));
            Optional<String> name = island.flatMap(found -> Reflect.call(found, "getName")).map(Object::toString)
                    .filter(text -> !text.isBlank());
            if (name.isPresent()) {
                return name;
            }
            return island.flatMap(found -> Reflect.call(found, "getOwner"))
                    .flatMap(chief -> Reflect.call(chief, "getUniqueId")).filter(UUID.class::isInstance)
                    .flatMap(id -> playerNames.apply((UUID) id));
        } catch (IllegalArgumentException notAUuid) {
            return Optional.empty();
        }
    }

    private static BankRole roleOf(Object player) {
        String role = Reflect.call(player, "getPlayerRole").flatMap(found -> Reflect.call(found, "getName"))
                .map(name -> name.toString().toUpperCase(Locale.ROOT)).orElse("");
        return role.contains("LEADER") || role.contains("ADMIN") || role.contains("MOD") ? BankRole.ADMIN
                : BankRole.MEMBER;
    }
}
