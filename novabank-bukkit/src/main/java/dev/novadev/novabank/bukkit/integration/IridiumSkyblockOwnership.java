package dev.novadev.novabank.bukkit.integration;

import dev.novadev.novabank.api.entity.BankRole;
import dev.novadev.novabank.api.entity.EntityMembership;
import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.entity.OwnershipProvider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Iles d'IridiumSkyblock (type {@code island}, identifiant = numero de l'ile). Par reflexion ; signatures VERIFIEES avec
 * IridiumSkyblock 4.1.5 (javap) : {@code IridiumSkyblockAPI.getInstance()}, {@code getUser(OfflinePlayer)},
 * {@code User.getIsland()} (Optional), {@code User.getUserRank()} (entier), {@code Rank.OWNER.getId()},
 * {@code Island.getId()}, {@code Island.getName()}, {@code getIslandById(int)} (Optional).
 * Roles : rang OWNER (-2) = OWNER, plus haut rang configure (co-proprietaire) = ADMIN, les autres = MEMBER.
 */
public final class IridiumSkyblockOwnership implements OwnershipProvider {

    private final Object api;
    private final int ownerRank;
    private final Supplier<Integer> topRank;

    private IridiumSkyblockOwnership(Object api, int ownerRank, Supplier<Integer> topRank) {
        this.api = api;
        this.ownerRank = ownerRank;
        this.topRank = topRank;
    }

    /**
     * @param loader chargeur de classes du plugin
     * @return le fournisseur, vide si l'API n'est pas trouvee
     */
    public static Optional<OwnershipProvider> create(ClassLoader loader) {
        Optional<Object> api = Reflect.type(loader, "com.iridium.iridiumskyblock.api.IridiumSkyblockAPI")
                .flatMap(type -> Reflect.call(null, type, "getInstance"));
        Optional<Integer> owner = Reflect.type(loader, "com.iridium.iridiumskyblock.dependencies.iridiumteams.Rank")
                .map(type -> java.util.Arrays.stream(type.getEnumConstants())
                        .filter(constant -> constant.toString().equals("OWNER")).findFirst()
                        .flatMap(constant -> Reflect.call(constant, "getId")).filter(Integer.class::isInstance)
                        .map(Integer.class::cast)).orElse(Optional.empty());
        return owner.isEmpty() || api.isEmpty() ? Optional.empty()
                : Optional.of(new IridiumSkyblockOwnership(api.get(), owner.get(), () -> topUserRank(loader)));
    }

    @Override
    public String entityType() {
        return EntityRef.ISLAND;
    }

    @Override
    public List<EntityMembership> resolve(UUID playerId) {
        Optional<Object> user = Reflect.call(api, "getUser", Bukkit.getOfflinePlayer(playerId));
        Optional<Object> island = user.flatMap(found -> Reflect.call(found, "getIsland")).flatMap(IridiumSkyblockOwnership::unwrap);
        Optional<String> id = island.flatMap(found -> Reflect.call(found, "getId")).map(Object::toString);
        if (id.isEmpty()) {
            return List.of();
        }
        int rank = user.flatMap(found -> Reflect.call(found, "getUserRank")).filter(Integer.class::isInstance)
                .map(Integer.class::cast).orElse(0);
        return List.of(new EntityMembership(new EntityRef(EntityRef.ISLAND, id.get()), role(rank, ownerRank, topRank.get())));
    }

    /**
     * Rangs d'IridiumSkyblock : proprietaire = constante OWNER (id -2) ; les autres rangs sont les entiers positifs de
     * {@code userRanks} (1 membre ... co-proprietaire en dernier). Le plus haut est {@code admin}.
     *
     * @param rank      rang du joueur
     * @param ownerRank id du rang proprietaire
     * @param topRank   plus haut rang configure ({@link Integer#MAX_VALUE} si inconnu : personne n'est admin)
     * @return le role NovaBank
     */
    static BankRole role(int rank, int ownerRank, int topRank) {
        return rank == ownerRank ? BankRole.OWNER : rank > 0 && rank == topRank ? BankRole.ADMIN : BankRole.MEMBER;
    }

    /** Plus haut rang de {@code userRanks} s'il y en a au moins deux, sinon {@link Integer#MAX_VALUE}. Relu a chaque fois. */
    private static int topUserRank(ClassLoader loader) {
        return Reflect.type(loader, "com.iridium.iridiumskyblock.IridiumSkyblock")
                .flatMap(type -> Reflect.call(null, type, "getInstance"))
                .flatMap(plugin -> Reflect.call(plugin, "getConfiguration"))
                .flatMap(config -> {
                    try {
                        return Optional.ofNullable(config.getClass().getField("userRanks").get(config));
                    } catch (ReflectiveOperationException | RuntimeException failure) {
                        return Optional.empty();
                    }
                })
                .filter(java.util.Map.class::isInstance).map(java.util.Map.class::cast)
                .filter(ranks -> ranks.size() > 1)
                .flatMap(ranks -> ((java.util.Map<?, ?>) ranks).keySet().stream().filter(Integer.class::isInstance)
                        .map(Integer.class::cast).max(Integer::compare))
                .orElse(Integer.MAX_VALUE);
    }

    @Override
    public Optional<String> displayName(EntityRef entity) {
        try {
            return Reflect.call(api, "getIslandById", Integer.parseInt(entity.id())).flatMap(IridiumSkyblockOwnership::unwrap)
                    .flatMap(island -> Reflect.call(island, "getName")).map(Object::toString);
        } catch (NumberFormatException notANumber) {
            return Optional.empty();
        }
    }

    private static Optional<Object> unwrap(Object value) {
        return value instanceof Optional<?> optional ? optional.map(Object.class::cast) : Optional.of(value);
    }
}
