package dev.novadev.novabank.config.section;

import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.valuation.ValuationMode;
import dev.novadev.novabank.config.model.BankSettings;
import dev.novadev.novabank.config.tree.ConfigNode;
import dev.novadev.novabank.config.tree.ConfigTrees;
import dev.novadev.novabank.core.delivery.UndeliveredPolicy;
import dev.novadev.novabank.core.text.ConsoleText;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Section {@code banks} : types d'entite actives, priorite de la banque active par
 * defaut (D10), mode de valeur (D8), politique des items non remis (D13) et droits par
 * role ({@link BankRolesParser}).
 */
public final class BanksSectionParser {

    private static final String ENTITY_TYPES = "entity-types";
    private static final String DEFAULT_PRIORITY = "default-priority";

    private BanksSectionParser() {
    }

    /**
     * Lit et valide la section.
     *
     * @param root racine de la configuration
     * @return la section
     */
    public static BankSettings parse(ConfigNode root) {
        ConfigNode node = root.section("banks");
        List<String> entityTypes = entityTypes(node);
        return new BankSettings(entityTypes,
                defaultPriority(node, entityTypes),
                node.enumValue("valuation-mode", ValuationMode.class, ValuationMode.DERIVED),
                node.enumValue("undelivered-policy", UndeliveredPolicy.class, UndeliveredPolicy.PENDING_DELIVERY),
                BankRolesParser.parse(node, entityTypes));
    }

    private static List<String> entityTypes(ConfigNode node) {
        List<String> raw = node.stringList(ENTITY_TYPES, List.of(EntityRef.PLAYER));
        if (raw.isEmpty()) {
            node.error(ENTITY_TYPES, ConsoleText.tr("au moins un type d'entite doit etre active"));
        }
        Set<String> types = new LinkedHashSet<>();
        for (int i = 0; i < raw.size(); i++) {
            String element = ConfigTrees.element(ENTITY_TYPES, i);
            Optional<String> type = normalizedType(raw.get(i));
            if (type.isEmpty()) {
                node.error(element,
                        ConsoleText.tr("type d'entite invalide '{0}' (attendu : lettres, chiffres, '_', '.' ou '-')",
                                raw.get(i)));
            } else if (!types.add(type.get())) {
                node.warning(element, ConsoleText.tr("type '{0}' en double : ignore", type.get()));
            }
        }
        return List.copyOf(types);
    }

    private static List<String> defaultPriority(ConfigNode node, List<String> entityTypes) {
        List<String> raw = node.stringList(DEFAULT_PRIORITY, entityTypes);
        Set<String> priority = new LinkedHashSet<>();
        for (int i = 0; i < raw.size(); i++) {
            String element = ConfigTrees.element(DEFAULT_PRIORITY, i);
            Optional<String> type = normalizedType(raw.get(i));
            if (type.isEmpty() || !entityTypes.contains(type.get())) {
                node.error(element, ConsoleText.tr("type '{0}' absent de banks.{1}", raw.get(i), ENTITY_TYPES));
            } else if (!priority.add(type.get())) {
                node.error(element, ConsoleText.tr("type '{0}' en double", type.get()));
            }
        }
        return List.copyOf(priority);
    }

    /** Reutilise la regle de l'API ({@link EntityRef}) plutot que de la dupliquer. */
    static Optional<String> normalizedType(String raw) {
        try {
            return Optional.of(new EntityRef(raw, "-").type());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
