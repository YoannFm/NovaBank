package dev.novadev.novabank.core.application;

import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.api.valuation.ValuationMode;
import dev.novadev.novabank.core.access.RoleRights;
import dev.novadev.novabank.core.text.ConsoleText;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Regles en vigueur pour les operations des joueurs, issues de la configuration. Un
 * instantane est pris au debut de chaque operation : un rechargement en cours de route ne
 * change pas les regles d'une operation commencee.
 *
 * @param enabledTypes            types d'entite actives
 * @param defaultPriority         ordre des types pour la banque active par defaut
 * @param roles                   droits par type d'entite (absent : {@link RoleRights#DEFAULT})
 * @param valuationMode           mode de valeur des banques creees
 * @param acceptedItems           items acceptes en depot, dans l'ordre de la configuration
 * @param maxQuantityPerOperation quantite maximale par operation et par item
 * @param cooldown                delai minimal entre deux operations d'un joueur
 * @param savePlayerData          sauvegarder les donnees du joueur apres chaque validation
 */
public record TransferRules(List<String> enabledTypes, List<String> defaultPriority, Map<String, RoleRights> roles,
                            ValuationMode valuationMode, List<ItemKey> acceptedItems, long maxQuantityPerOperation,
                            Duration cooldown, boolean savePlayerData) {

    /**
     * Fige les collections et valide les bornes.
     *
     * @throws IllegalArgumentException si la quantite maximale n'est pas positive ou le delai negatif
     */
    public TransferRules {
        enabledTypes = List.copyOf(enabledTypes);
        defaultPriority = List.copyOf(defaultPriority);
        roles = Map.copyOf(roles);
        Objects.requireNonNull(valuationMode, "valuationMode");
        acceptedItems = List.copyOf(acceptedItems);
        Objects.requireNonNull(cooldown, "cooldown");
        if (maxQuantityPerOperation < 1 || cooldown.isNegative()) {
            throw new IllegalArgumentException(ConsoleText.tr("Limites invalides : {0} / {1}",
                    maxQuantityPerOperation, cooldown));
        }
    }

    /**
     * @param entityType type d'entite
     * @return les droits de ce type
     */
    public RoleRights rightsFor(String entityType) {
        return roles.getOrDefault(entityType, RoleRights.DEFAULT);
    }

    /**
     * @param item item
     * @return {@code true} si l'item est accepte en depot
     */
    public boolean accepts(ItemKey item) {
        return acceptedItems.contains(item);
    }
}
