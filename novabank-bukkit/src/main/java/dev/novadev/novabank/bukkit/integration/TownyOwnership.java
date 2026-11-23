package dev.novadev.novabank.bukkit.integration;

import dev.novadev.novabank.api.entity.BankRole;
import dev.novadev.novabank.api.entity.EntityMembership;
import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.entity.OwnershipProvider;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Villes de Towny (type {@code town}, identifiant = UUID de la ville). Par reflexion, d'apres l'API documentee ;
 * signatures VERIFIEES avec Towny 0.103.2.0 (javap) : {@code TownyAPI.getInstance()}, {@code getResident(UUID)},
 * {@code Resident.getTownOrNull()}, {@code Resident.isMayor()}, {@code Town.getUUID()}, {@code Town.getName()},
 * {@code TownyAPI.getTown(UUID)}.
 *
 * <p>Roles : le maire est {@code OWNER} ; un habitant avec le rang {@code assistant} est {@code ADMIN} ; les autres
 * habitants sont {@code MEMBER}.
 */
public final class TownyOwnership implements OwnershipProvider {

    /** Type d'entite des villes. */
    public static final String TOWN = "town";

    private final Object api;

    private TownyOwnership(Object api) {
        this.api = api;
    }

    /**
     * @param loader chargeur de classes du plugin
     * @return le fournisseur, vide si l'API n'est pas trouvee
     */
    public static Optional<OwnershipProvider> create(ClassLoader loader) {
        return Reflect.type(loader, "com.palmergames.bukkit.towny.TownyAPI")
                .flatMap(type -> Reflect.call(null, type, "getInstance")).map(TownyOwnership::new);
    }

    @Override
    public String entityType() {
        return TOWN;
    }

    @Override
    public List<EntityMembership> resolve(UUID playerId) {
        Optional<Object> resident = Reflect.call(api, "getResident", playerId);
        Optional<Object> town = resident.flatMap(found -> Reflect.call(found, "getTownOrNull"));
        Optional<String> id = town.flatMap(found -> Reflect.call(found, "getUUID")).map(Object::toString);
        if (id.isEmpty()) {
            return List.of();
        }
        boolean mayor = resident.flatMap(found -> Reflect.call(found, "isMayor")).map(Boolean.TRUE::equals).orElse(false);
        boolean assistant = resident.flatMap(found -> Reflect.call(found, "hasTownRank", "assistant"))
                .map(Boolean.TRUE::equals).orElse(false);
        BankRole role = mayor ? BankRole.OWNER : assistant ? BankRole.ADMIN : BankRole.MEMBER;
        return List.of(new EntityMembership(new EntityRef(TOWN, id.get()), role));
    }

    @Override
    public Optional<String> displayName(EntityRef entity) {
        try {
            return Reflect.call(api, "getTown", UUID.fromString(entity.id()))
                    .flatMap(town -> Reflect.call(town, "getName")).map(Object::toString);
        } catch (IllegalArgumentException notAUuid) {
            return Optional.empty();
        }
    }
}
