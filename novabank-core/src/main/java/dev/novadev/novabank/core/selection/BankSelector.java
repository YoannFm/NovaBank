package dev.novadev.novabank.core.selection;

import dev.novadev.novabank.api.entity.EntityMembership;
import dev.novadev.novabank.api.entity.EntityRef;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Regle de choix de la banque active (D10), fonction pure.
 *
 * <ol>
 *   <li>l'entite choisie par le joueur, s'il en est toujours membre ;</li>
 *   <li>sinon la premiere appartenance du premier type de la priorite par defaut ;</li>
 *   <li>sinon la premiere appartenance (type actif hors priorite).</li>
 * </ol>
 */
public final class BankSelector {

    private BankSelector() {
    }

    /**
     * @param memberships appartenances courantes du joueur
     * @param chosen      choix enregistre, ou vide
     * @param priority    ordre des types pour le choix par defaut
     * @return l'appartenance active, ou vide si le joueur n'en a aucune
     */
    public static Optional<EntityMembership> select(List<EntityMembership> memberships, Optional<EntityRef> chosen,
                                                    List<String> priority) {
        Objects.requireNonNull(memberships, "memberships");
        Objects.requireNonNull(chosen, "chosen");
        Objects.requireNonNull(priority, "priority");
        if (chosen.isPresent()) {
            Optional<EntityMembership> still = memberships.stream()
                    .filter(membership -> membership.entity().equals(chosen.get()))
                    .findFirst();
            if (still.isPresent()) {
                return still;
            }
        }
        for (String type : priority) {
            Optional<EntityMembership> first = firstOfType(memberships, type);
            if (first.isPresent()) {
                return first;
            }
        }
        return memberships.stream().findFirst();
    }

    /**
     * Banque d'un type impose (commande d'un hote) : l'entite preferee par l'hote, sinon celle deja choisie par le
     * joueur, sinon la premiere ; chacune seulement si le joueur en est encore membre.
     *
     * @param memberships appartenances courantes
     * @param type        type impose
     * @param preferred   entite preferee par l'hote (lieu, commande), ou vide
     * @param chosen      choix enregistre du joueur, ou vide
     * @return l'appartenance retenue, ou vide si le joueur n'a aucune entite de ce type
     */
    public static Optional<EntityMembership> selectOfType(List<EntityMembership> memberships, String type,
                                                          Optional<EntityRef> preferred, Optional<EntityRef> chosen) {
        for (Optional<EntityRef> wanted : List.of(preferred, chosen)) {
            Optional<EntityMembership> found = wanted.filter(entity -> entity.type().equals(type))
                    .flatMap(entity -> memberships.stream().filter(membership -> membership.entity().equals(entity))
                            .findFirst());
            if (found.isPresent()) {
                return found;
            }
        }
        return firstOfType(memberships, type);
    }

    /**
     * @param memberships appartenances
     * @param type        type recherche
     * @return la premiere appartenance de ce type, ou vide
     */
    public static Optional<EntityMembership> firstOfType(List<EntityMembership> memberships, String type) {
        return memberships.stream().filter(membership -> membership.entity().type().equals(type)).findFirst();
    }
}
