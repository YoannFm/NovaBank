package dev.novadev.novabank.config.model;

import dev.novadev.novabank.api.valuation.ValuationMode;
import dev.novadev.novabank.core.access.RoleRights;
import dev.novadev.novabank.core.delivery.UndeliveredPolicy;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Section {@code banks}.
 *
 * @param entityTypes        types d'entite actives (normalises en minuscules, sans doublon)
 * @param defaultPriority    ordre des types pour choisir la banque active par defaut (D10) ;
 *                           chaque entree est un type active
 * @param valuationMode      mode de calcul de la valeur (D8)
 * @param undeliveredPolicy  sort des items non remis lors d'un retrait (D13)
 * @param roles              droits de depot et de retrait par role, un par type active
 */
public record BankSettings(List<String> entityTypes, List<String> defaultPriority, ValuationMode valuationMode,
                           UndeliveredPolicy undeliveredPolicy, Map<String, RoleRights> roles) {

    /**
     * Fige les listes et verifie la presence des champs.
     *
     * @throws NullPointerException si un champ est nul
     */
    public BankSettings {
        entityTypes = List.copyOf(entityTypes);
        defaultPriority = List.copyOf(defaultPriority);
        Objects.requireNonNull(valuationMode, "valuationMode");
        Objects.requireNonNull(undeliveredPolicy, "undeliveredPolicy");
        roles = Collections.unmodifiableMap(new LinkedHashMap<>(roles));
    }
}
