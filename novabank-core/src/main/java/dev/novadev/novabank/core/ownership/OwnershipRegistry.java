package dev.novadev.novabank.core.ownership;

import dev.novadev.novabank.api.entity.EntityMembership;
import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.entity.OwnershipProvider;
import dev.novadev.novabank.api.operation.OperationType;
import dev.novadev.novabank.core.text.ConsoleText;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fournisseurs d'appartenance enregistres, un par type d'entite.
 *
 * <p>Sur pour plusieurs threads. Un fournisseur qui echoue (exception) est ecarte pour
 * l'operation en cours et signale au journal : une integration tierce defaillante ne
 * bloque pas les autres banques du joueur.
 */
public final class OwnershipRegistry {

    private static final System.Logger LOGGER = System.getLogger("NovaBank");

    private static final Set<OperationType> HOST_DECIDED = Set.of(OperationType.DEPOSIT, OperationType.WITHDRAW);

    private final Map<String, OwnershipProvider> providers = new ConcurrentHashMap<>();

    /**
     * Enregistre un fournisseur.
     *
     * @param provider fournisseur
     * @throws IllegalStateException si un fournisseur gere deja ce type
     */
    public void register(OwnershipProvider provider) {
        Objects.requireNonNull(provider, "provider");
        String type = new EntityRef(provider.entityType(), "-").type();
        if (providers.putIfAbsent(type, provider) != null) {
            throw new IllegalStateException(ConsoleText.tr("Un fournisseur gere deja le type d'entite '{0}'", type));
        }
    }

    /**
     * Retire le fournisseur d'un type (plugin tiers desactive).
     *
     * @param entityType type d'entite
     */
    public void unregister(String entityType) {
        providers.remove(entityType);
    }

    /**
     * Appartenances d'un joueur pour les types actives, dans l'ordre des types. A appeler
     * sur le thread du joueur.
     *
     * @param playerId     joueur
     * @param enabledTypes types actives par la configuration
     * @return ses appartenances ; un type sans fournisseur n'en produit aucune
     */
    public List<EntityMembership> memberships(UUID playerId, Collection<String> enabledTypes) {
        Objects.requireNonNull(playerId, "playerId");
        List<EntityMembership> memberships = new ArrayList<>();
        for (String type : enabledTypes) {
            OwnershipProvider provider = providers.get(type);
            if (provider != null) {
                memberships.addAll(resolve(provider, type, playerId));
            }
        }
        return List.copyOf(memberships);
    }

    /**
     * Droits decides par les plugins hotes pour ces appartenances. A appeler sur le thread du joueur. Un fournisseur
     * en echec ne decide rien : la regle des roles s'applique.
     *
     * @param playerId    joueur
     * @param memberships ses appartenances
     * @return decisions de l'hote par entite et par operation (depot, retrait) ; une decision absente suit la regle
     *         {@code banks.roles}
     */
    public Map<EntityRef, Map<OperationType, Boolean>> rights(UUID playerId, List<EntityMembership> memberships) {
        Map<EntityRef, Map<OperationType, Boolean>> rights = new java.util.HashMap<>();
        for (EntityMembership membership : memberships) {
            OwnershipProvider provider = providers.get(membership.entity().type());
            if (provider == null) {
                continue;
            }
            Map<OperationType, Boolean> decided = new java.util.EnumMap<>(OperationType.class);
            for (OperationType type : HOST_DECIDED) {
                try {
                    provider.allows(playerId, membership.entity(), type).ifPresent(allowed -> decided.put(type, allowed));
                } catch (RuntimeException failure) {
                    LOGGER.log(System.Logger.Level.WARNING, ConsoleText.tr("Droit de {0} sur {1} indisponible : {2}",
                            type, membership.entity(), failure));
                }
            }
            if (!decided.isEmpty()) {
                rights.put(membership.entity(), Map.copyOf(decided));
            }
        }
        return Map.copyOf(rights);
    }

    /**
     * Entite preferee par le fournisseur d'un type ; un fournisseur en echec ne prefere rien.
     *
     * @param playerId   joueur (thread du joueur)
     * @param entityType type
     * @param command    commande tapee, vide hors commande d'hote
     * @return l'entite preferee, ou vide
     */
    public Optional<EntityRef> preferred(UUID playerId, String entityType, String command) {
        OwnershipProvider provider = providers.get(entityType);
        if (provider == null) {
            return Optional.empty();
        }
        try {
            return provider.preferred(playerId, command).map(id -> new EntityRef(entityType, id));
        } catch (RuntimeException failure) {
            LOGGER.log(System.Logger.Level.WARNING, ConsoleText.tr("Entite preferee de type {0} indisponible : {1}",
                    entityType, failure));
            return Optional.empty();
        }
    }

    /**
     * @return les types d'entite pris en charge (un fournisseur enregistre), tries
     */
    public java.util.SortedSet<String> types() {
        return new java.util.TreeSet<>(providers.keySet());
    }

    /**
     * Nom affichable d'une entite : celui de son fournisseur, sinon son identifiant.
     *
     * @param entity entite
     * @return le nom a afficher
     */
    public String displayName(EntityRef entity) {
        Objects.requireNonNull(entity, "entity");
        OwnershipProvider provider = providers.get(entity.type());
        if (provider == null) {
            return entity.id();
        }
        try {
            return provider.displayName(entity).orElse(entity.id());
        } catch (RuntimeException failure) {
            LOGGER.log(System.Logger.Level.WARNING, ConsoleText.tr("Nom de l'entite {0} indisponible : {1}",
                    entity, failure));
            return entity.id();
        }
    }

    private static List<EntityMembership> resolve(OwnershipProvider provider, String type, UUID playerId) {
        try {
            return provider.resolve(playerId).stream()
                    .filter(membership -> membership.entity().type().equals(type))
                    .toList();
        } catch (RuntimeException failure) {
            LOGGER.log(System.Logger.Level.WARNING,
                    ConsoleText.tr("Fournisseur d'appartenance '{0}' en echec pour {1} : {2}",
                            type, playerId, failure), failure);
            return List.of();
        }
    }
}
