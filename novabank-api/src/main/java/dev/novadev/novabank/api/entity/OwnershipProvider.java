package dev.novadev.novabank.api.entity;

import dev.novadev.novabank.api.operation.OperationType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Fournisseur d'appartenances pour un type d'entite : indique a quelles entites de ce
 * type un joueur appartient, et avec quel role.
 *
 * <p>NovaBank fournit le type {@code player} (chaque joueur est {@link BankRole#OWNER} de
 * sa banque personnelle). Les integrations tierces (ile, faction, ville...) enregistrent
 * leur propre fournisseur.
 *
 * <p>Fils d'execution : {@link #resolve(UUID)} est appele sur le thread du joueur
 * (thread principal sous Bukkit, thread de la region du joueur sous Folia), car la plupart
 * des API tierces l'exigent. Il doit etre
 * rapide et ne jamais faire d'entree/sortie bloquante. Le resultat sert a une seule
 * operation : NovaBank ne le met pas en cache, une appartenance retiree (exclusion,
 * dissolution) est donc prise en compte des l'operation suivante.
 */
public interface OwnershipProvider {

    /**
     * Type d'entite gere, au format de {@link EntityRef#type()} (minuscules).
     *
     * @return le type, par exemple {@code island}
     */
    String entityType();

    /**
     * Entites de ce type auxquelles le joueur appartient.
     *
     * @param playerId joueur
     * @return ses appartenances (toutes du type {@link #entityType()}), vide s'il n'en a aucune
     */
    List<EntityMembership> resolve(UUID playerId);

    /**
     * Nom lisible d'une entite de ce type, pour l'affichage.
     *
     * @param entity entite du type {@link #entityType()}
     * @return le nom, ou vide s'il est inconnu (l'identifiant sera affiche)
     */
    Optional<String> displayName(EntityRef entity);

    /**
     * Droit decide par le plugin hote lui-meme (privilege d'ile, flag, rang...), qui remplace alors la regle
     * {@code banks.roles} de NovaBank pour cette operation. Appele sur le thread du joueur, comme {@link #resolve(UUID)}.
     *
     * @param playerId joueur
     * @param entity   entite du type {@link #entityType()} a laquelle il appartient
     * @param type     {@link OperationType#DEPOSIT} ou {@link OperationType#WITHDRAW}
     * @return la decision de l'hote, ou vide pour appliquer la regle des roles (comportement par defaut)
     * @since 1.0.0
     */
    default Optional<Boolean> allows(UUID playerId, EntityRef entity, OperationType type) {
        return Optional.empty();
    }

    /**
     * Entite de ce type a preferer quand le joueur en a plusieurs : celle ou il se trouve, ou celle que designe la
     * commande tapee (BentoBox : {@code /ob bank} vise l'ile AOneBlock). Appele sur le thread du joueur, avant le
     * choix de la banque d'une commande d'hote. Une entite dont le joueur n'est pas membre est ignoree.
     *
     * @param playerId joueur
     * @param command  commande tapee sans la barre ({@code ob bank}), vide hors commande d'hote
     * @return l'identifiant de l'entite preferee, ou vide (choix precedent du joueur, sinon la premiere)
     * @since 1.0.0
     */
    default Optional<String> preferred(UUID playerId, String command) {
        return Optional.empty();
    }
}
