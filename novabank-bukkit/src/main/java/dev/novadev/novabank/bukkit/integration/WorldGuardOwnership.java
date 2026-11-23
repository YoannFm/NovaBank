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
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Regions WorldGuard (type {@code region}, identifiant = {@code monde:region}) dont le joueur est PROPRIETAIRE ({@code OWNER}) ou
 * membre ({@code MEMBER}). CDC 5.6 : une region n'a pas toujours de proprietaire clair ; la banque d'une region n'est utilisee
 * que si le type {@code region} est active dans {@code banks.entity-types}. Le parcours de toutes les regions est
 * proportionnel a leur nombre (a eviter sur de tres gros serveurs). Par reflexion ; signatures NON verifiees :
 * {@code WorldGuard.getInstance().getPlatform().getRegionContainer().getLoaded()}, {@code RegionManager.getName()},
 * {@code getRegions()}, {@code ProtectedRegion.getId()}, {@code getOwners().contains(UUID)}, {@code getMembers().contains(UUID)}.
 */
public final class WorldGuardOwnership implements OwnershipProvider {

    private final Object container;

    private WorldGuardOwnership(Object container) {
        this.container = container;
    }

    /**
     * @param loader chargeur de classes du plugin
     * @return le fournisseur, vide si l'API n'est pas trouvee
     */
    public static Optional<OwnershipProvider> create(ClassLoader loader) {
        return Reflect.type(loader, "com.sk89q.worldguard.WorldGuard")
                .flatMap(type -> Reflect.call(null, type, "getInstance"))
                .flatMap(instance -> Reflect.call(instance, "getPlatform"))
                .flatMap(platform -> Reflect.call(platform, "getRegionContainer")).map(WorldGuardOwnership::new);
    }

    @Override
    public String entityType() {
        return "region";
    }

    @Override
    public List<EntityMembership> resolve(UUID playerId) {
        List<EntityMembership> result = new ArrayList<>();
        Object managers = Reflect.call(container, "getLoaded").orElse(null);
        if (!(managers instanceof Collection<?> all)) {
            return List.of();
        }
        for (Object manager : all) {
            String world = Reflect.call(manager, "getName").map(Object::toString).orElse("");
            Object regions = Reflect.call(manager, "getRegions").orElse(null);
            if (!(regions instanceof java.util.Map<?, ?> byName)) {
                continue;
            }
            for (Object region : byName.values()) {
                boolean owner = contains(Reflect.call(region, "getOwners"), playerId);
                boolean member = !owner && contains(Reflect.call(region, "getMembers"), playerId);
                Optional<String> id = Reflect.call(region, "getId").map(Object::toString);
                if ((owner || member) && id.isPresent() && !id.get().equals("__global__")) {
                    result.add(new EntityMembership(new EntityRef("region", world + ":" + id.get()),
                            owner ? BankRole.OWNER : BankRole.MEMBER));
                }
            }
        }
        return List.copyOf(result);
    }

    /**
     * La region du joueur (proprietaire ou membre) de plus haute priorite ou il se trouve. Seules les regions de ce point
     * sont lues ({@code RegionManager.getApplicableRegions(BlockVector3)}, {@code BlockVector3.at(int,int,int)},
     * {@code ProtectedRegion.getPriority()} : verifies sur WorldGuard 7.0.17 et WorldEdit 7.4.6).
     */
    @Override
    public Optional<String> preferred(UUID playerId, String command) {
        Optional<org.bukkit.Location> place = PlayerPlace.of(playerId);
        Object managers = Reflect.call(container, "getLoaded").orElse(null);
        if (place.isEmpty() || place.get().getWorld() == null || !(managers instanceof Collection<?> all)) {
            return Optional.empty();
        }
        org.bukkit.Location at = place.get();
        String world = at.getWorld().getName();
        Optional<Object> point = Reflect.type(container.getClass().getClassLoader(), "com.sk89q.worldedit.math.BlockVector3")
                .flatMap(type -> Reflect.call(null, type, "at", at.getBlockX(), at.getBlockY(), at.getBlockZ()));
        String best = null;
        int bestPriority = Integer.MIN_VALUE;
        for (Object manager : all) {
            if (point.isEmpty() || !world.equals(Reflect.call(manager, "getName").map(Object::toString).orElse(""))) {
                continue;
            }
            Object regions = Reflect.call(manager, "getApplicableRegions", point.get()).orElse(null);
            if (!(regions instanceof Iterable<?> here)) {
                continue;
            }
            for (Object region : here) {
                int priority = Reflect.call(region, "getPriority").filter(Integer.class::isInstance)
                        .map(Integer.class::cast).orElse(0);
                boolean member = contains(Reflect.call(region, "getOwners"), playerId)
                        || contains(Reflect.call(region, "getMembers"), playerId);
                Optional<String> id = Reflect.call(region, "getId").map(Object::toString);
                if (member && id.isPresent() && priority >= bestPriority) {
                    best = world + ":" + id.get();
                    bestPriority = priority;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private static boolean contains(Optional<Object> domain, UUID playerId) {
        return domain.flatMap(found -> Reflect.call(found, "contains", playerId)).map(Boolean.TRUE::equals).orElse(false);
    }

    @Override
    public Optional<String> displayName(EntityRef entity) {
        return Optional.of(entity.id());
    }
}
