package dev.novadev.novabank.bukkit.integration;

import dev.novadev.novabank.api.entity.BankRole;
import dev.novadev.novabank.api.entity.EntityMembership;
import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.entity.OwnershipProvider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Iles de BentoBox (BSkyBlock, AOneBlock...), type {@code island}, identifiant = identifiant unique de l'ile. Par
 * reflexion, d'apres l'API documentee ; signatures VERIFIEES avec BentoBox 3.23.4 (javap) :
 * {@code BentoBox.getInstance().getIslands()}, {@code IslandsManager.getIslands(UUID)} et {@code getIslandById(String)},
 * {@code Island.getUniqueId()}, {@code getRank(UUID)}, {@code getOwner()}, {@code getName()}.
 *
 * <p>Rangs BentoBox : 1000 proprietaire, 900 sous-proprietaire ({@code ADMIN}), 500 membre ({@code MEMBER}) ; les rangs
 * inferieurs (visiteur, coop, confiance) ne donnent pas acces a la banque. Un joueur membre de plusieurs iles (plusieurs
 * modes de jeu) obtient plusieurs appartenances : la commande d'un mode de jeu ({@code /ob bank}) prend l'ile ou il se
 * trouve, sinon celle de ce mode de jeu ({@link #preferred}). Signatures VERIFIEES (3.23.4) :
 * {@code IslandsManager.getIslandAt(Location)}, {@code AddonsManager.getGameModeAddons()},
 * {@code GameModeAddon.getPlayerCommand()}, {@code inWorld(World)}, {@code Island.getWorld()}.
 */
public final class BentoBoxOwnership implements OwnershipProvider {

    private static final int OWNER_RANK = 1000;
    private static final int SUB_OWNER_RANK = 900;
    private static final int MEMBER_RANK = 500;
    private static final String PLUGIN_CLASS = "world.bentobox.bentobox.BentoBox";

    private final Class<?> plugin;
    private final Function<UUID, Optional<String>> playerNames;

    private BentoBoxOwnership(Class<?> plugin, Function<UUID, Optional<String>> playerNames) {
        this.plugin = plugin;
        this.playerNames = playerNames;
    }

    /**
     * @param loader      chargeur de classes du plugin
     * @param playerNames nom connu d'un joueur
     * @return le fournisseur, vide si l'API n'est pas trouvee
     */
    public static Optional<OwnershipProvider> create(ClassLoader loader, Function<UUID, Optional<String>> playerNames) {
        return Reflect.type(loader, PLUGIN_CLASS).map(plugin -> new BentoBoxOwnership(plugin, playerNames));
    }

    @Override
    public String entityType() {
        return EntityRef.ISLAND;
    }

    @Override
    public List<EntityMembership> resolve(UUID playerId) {
        List<EntityMembership> memberships = new ArrayList<>();
        Optional<Object> islands = islands();
        Object found = islands.flatMap(manager -> Reflect.call(manager, "getIslands", playerId)).orElse(null);
        if (found instanceof Collection<?> all) {
            for (Object island : all) {
                int rank = Reflect.call(island, "getRank", playerId).filter(Integer.class::isInstance)
                        .map(Integer.class::cast).orElse(0);
                Optional<String> id = Reflect.call(island, "getUniqueId").map(Object::toString);
                if (rank >= MEMBER_RANK && id.isPresent()) {
                    BankRole role = rank >= OWNER_RANK ? BankRole.OWNER
                            : rank >= SUB_OWNER_RANK ? BankRole.ADMIN : BankRole.MEMBER;
                    memberships.add(new EntityMembership(new EntityRef(EntityRef.ISLAND, id.get()), role));
                }
            }
        }
        return List.copyOf(memberships);
    }

    @Override
    public Optional<String> displayName(EntityRef entity) {
        Optional<Object> island = islands().flatMap(manager -> Reflect.call(manager, "getIslandById", entity.id()))
                .flatMap(BentoBoxOwnership::unwrap);
        Optional<String> name = island.flatMap(found -> Reflect.call(found, "getName")).map(Object::toString)
                .filter(text -> !text.isBlank());
        if (name.isPresent()) {
            return name;
        }
        return island.flatMap(found -> Reflect.call(found, "getOwner")).filter(UUID.class::isInstance)
                .flatMap(id -> playerNames.apply((UUID) id));
    }

    /**
     * L'ile ou se trouve le joueur, sinon son ile dans le mode de jeu de la commande ({@code ob bank} : AOneBlock),
     * sinon son ile dans le monde ou il se trouve.
     */
    @Override
    public Optional<String> preferred(UUID playerId, String command) {
        Optional<Object> manager = islands();
        Optional<org.bukkit.Location> place = PlayerPlace.of(playerId);
        Optional<String> here = manager.flatMap(found -> place.flatMap(at -> Reflect.call(found, "getIslandAt", at)))
                .flatMap(BentoBoxOwnership::unwrap).filter(island -> isMember(island, playerId))
                .flatMap(island -> Reflect.call(island, "getUniqueId")).map(Object::toString);
        if (here.isPresent()) {
            return here;
        }
        Optional<org.bukkit.World> world = gameModeWorld(command.split(" ", 2)[0])
                .or(() -> place.map(org.bukkit.Location::getWorld));
        return manager.flatMap(found -> world.flatMap(at -> Reflect.call(found, "getIsland", at, playerId)))
                .flatMap(island -> Reflect.call(island, "getUniqueId")).map(Object::toString);
    }

    private static boolean isMember(Object island, UUID playerId) {
        return Reflect.call(island, "getRank", playerId).filter(Integer.class::isInstance).map(Integer.class::cast)
                .orElse(0) >= MEMBER_RANK;
    }

    /** Monde principal du mode de jeu dont la commande joueur (ou un alias) est {@code label}. */
    private Optional<org.bukkit.World> gameModeWorld(String label) {
        Object modes = Reflect.call(null, plugin, "getInstance").flatMap(instance -> Reflect.call(instance, "getAddonsManager"))
                .flatMap(addons -> Reflect.call(addons, "getGameModeAddons")).orElse(null);
        if (label.isEmpty() || !(modes instanceof Collection<?> all)) {
            return Optional.empty();
        }
        for (Object mode : all) {
            Optional<Object> command = Reflect.call(mode, "getPlayerCommand").flatMap(BentoBoxOwnership::unwrap);
            boolean named = command.filter(org.bukkit.command.Command.class::isInstance)
                    .map(org.bukkit.command.Command.class::cast)
                    .map(found -> found.getLabel().equalsIgnoreCase(label)
                            || found.getAliases().stream().anyMatch(label::equalsIgnoreCase)).orElse(false);
            if (named) {
                return Reflect.call(mode, "getOverWorld").filter(org.bukkit.World.class::isInstance)
                        .map(org.bukkit.World.class::cast);
            }
        }
        return Optional.empty();
    }

    private Optional<Object> islands() {
        return Reflect.call(null, plugin, "getInstance").flatMap(instance -> Reflect.call(instance, "getIslands"));
    }

    /** {@code getIslandById} rend un {@code Optional} dans les versions recentes. */
    private static Optional<Object> unwrap(Object value) {
        return value instanceof Optional<?> optional ? optional.map(Object.class::cast) : Optional.of(value);
    }
}
