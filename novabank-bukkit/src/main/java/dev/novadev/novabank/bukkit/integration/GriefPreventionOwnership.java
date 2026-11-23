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
 * Claims de GriefPrevention (type {@code claim}, identifiant = numero du claim). Seuls les claims dont le joueur est
 * PROPRIETAIRE donnent une banque (OWNER) ; les joueurs de confiance ne sont pas lus. Un joueur qui possede plusieurs
 * claims a plusieurs banques : la commande prend le claim ou il se trouve (le claim principal pour une subdivision),
 * sinon son choix precedent, sinon le premier. Par reflexion ; signatures NON verifiees :
 * {@code GriefPrevention.instance.dataStore.getPlayerData(UUID).getClaims()}, {@code Claim.getID()} ; VERIFIEES :
 * {@code DataStore.getClaimAt(Location, boolean, Claim)}, champ {@code Claim.parent}.
 */
public final class GriefPreventionOwnership implements OwnershipProvider {

    private final Object dataStore;

    private GriefPreventionOwnership(Object dataStore) {
        this.dataStore = dataStore;
    }

    /**
     * @param loader chargeur de classes du plugin
     * @return le fournisseur, vide si l'API n'est pas trouvee
     */
    public static Optional<OwnershipProvider> create(ClassLoader loader) {
        try {
            return Reflect.type(loader, "me.ryanhamshire.GriefPrevention.GriefPrevention").map(type -> {
                try {
                    Object plugin = type.getField("instance").get(null);
                    return plugin == null ? null : plugin.getClass().getField("dataStore").get(plugin);
                } catch (ReflectiveOperationException | RuntimeException failure) {
                    return null;
                }
            }).map(GriefPreventionOwnership::new);
        } catch (RuntimeException failure) {
            return Optional.empty();
        }
    }

    @Override
    public String entityType() {
        return "claim";
    }

    @Override
    public List<EntityMembership> resolve(UUID playerId) {
        List<EntityMembership> result = new ArrayList<>();
        Object claims = Reflect.call(dataStore, "getPlayerData", playerId).flatMap(data -> Reflect.call(data, "getClaims"))
                .orElse(null);
        if (claims instanceof Collection<?> all) {
            for (Object claim : all) {
                Reflect.call(claim, "getID").ifPresent(id -> result.add(new EntityMembership(
                        new EntityRef("claim", id.toString()), BankRole.OWNER)));
            }
        }
        return List.copyOf(result);
    }

    @Override
    public Optional<String> preferred(UUID playerId, String command) {
        return PlayerPlace.of(playerId).flatMap(at -> Reflect.call(dataStore, "getClaimAt", at, false, null))
                .map(GriefPreventionOwnership::topLevel).flatMap(claim -> Reflect.call(claim, "getID"))
                .map(Object::toString);
    }

    /** Une subdivision n'a pas de banque propre : celle du claim principal. */
    private static Object topLevel(Object claim) {
        Object current = claim;
        try {
            for (Object parent = current.getClass().getField("parent").get(current); parent != null;
                 parent = parent.getClass().getField("parent").get(parent)) {
                current = parent;
            }
        } catch (ReflectiveOperationException | RuntimeException absent) {
            // champ absent dans cette version : le claim trouve est garde
        }
        return current;
    }

    @Override
    public Optional<String> displayName(EntityRef entity) {
        return Optional.of("claim #" + entity.id());
    }
}
