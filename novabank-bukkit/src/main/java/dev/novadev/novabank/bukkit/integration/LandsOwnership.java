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
 * Terres de Lands (type {@code land}, identifiant = ULID de la terre). Par reflexion ; signatures VERIFIEES avec Lands 7.26.3 (javap) :
 * {@code LandsIntegration.of(Plugin)}, {@code getLandPlayer(UUID)}, {@code LandPlayer.getLands()},
 * {@code Land.getULID()}, {@code Land.getName()}, {@code Land.getOwnerUID()}, {@code Land.getLandByULID(ULID)}.
 * Roles : le proprietaire = OWNER, les autres membres = MEMBER. Un joueur de plusieurs terres : la commande prend la
 * terre ou il se trouve ({@code LandsIntegration.getArea(Location)}, {@code Area.getLand()}, verifies), sinon son choix
 * precedent, sinon la premiere.
 */
public final class LandsOwnership implements OwnershipProvider {

    private final Object integration;

    private LandsOwnership(Object integration) {
        this.integration = integration;
    }

    /**
     * @param loader chargeur de classes du plugin
     * @param owner  plugin NovaBank, exige par Lands pour identifier l'integration
     * @return le fournisseur, vide si l'API n'est pas trouvee
     */
    public static Optional<OwnershipProvider> create(ClassLoader loader, Plugin owner) {
        return Reflect.type(loader, "me.angeschossen.lands.api.LandsIntegration")
                .flatMap(type -> Reflect.call(null, type, "of", owner)).map(LandsOwnership::new);
    }

    @Override
    public String entityType() {
        return "land";
    }

    @Override
    public List<EntityMembership> resolve(UUID playerId) {
        List<EntityMembership> result = new ArrayList<>();
        Object lands = Reflect.call(integration, "getLandPlayer", playerId).flatMap(found -> Reflect.call(found, "getLands"))
                .orElse(null);
        if (lands instanceof Collection<?> all) {
            for (Object land : all) {
                Optional<String> id = Reflect.call(land, "getULID").map(Object::toString);
                boolean owner = Reflect.call(land, "getOwnerUID").map(playerId::equals).orElse(false);
                if (id.isPresent()) {
                    result.add(new EntityMembership(new EntityRef("land", id.get()), owner ? BankRole.OWNER : BankRole.MEMBER));
                }
            }
        }
        return List.copyOf(result);
    }

    @Override
    public Optional<String> preferred(UUID playerId, String command) {
        return PlayerPlace.of(playerId).flatMap(at -> Reflect.call(integration, "getArea", at))
                .flatMap(area -> Reflect.call(area, "getLand")).flatMap(land -> Reflect.call(land, "getULID"))
                .map(Object::toString);
    }

    @Override
    public Optional<String> displayName(EntityRef entity) {
        return Reflect.type(integration.getClass().getClassLoader(), "me.angeschossen.lands.api.applicationframework.util.ULID")
                .flatMap(type -> Reflect.call(null, type, "fromString", entity.id()))
                .flatMap(ulid -> Reflect.call(integration, "getLandByULID", ulid))
                .flatMap(land -> Reflect.call(land, "getName")).map(Object::toString);
    }
}
