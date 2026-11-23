package dev.novadev.novabank.config.section;

import static dev.novadev.novabank.config.section.ConfigPatterns.IDENTIFIER;
import static dev.novadev.novabank.config.section.ConfigPatterns.IDENTIFIER_HINT;

import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.config.catalog.ItemAvailability;
import dev.novadev.novabank.config.model.AcceptedItem;
import dev.novadev.novabank.config.tree.ConfigNode;
import dev.novadev.novabank.core.text.ConsoleText;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Section {@code items} : liste des items acceptes et de leur prix.
 *
 * <p>Erreurs : cle absente ou invalide (D12, sans normalisation), cle en double, prix
 * absent, non numerique ou negatif. Avertissements : item inexistant sur ce serveur ou
 * fournisseur d'items custom absent ; l'item est alors ignore. Seuls les items
 * disponibles figurent dans le resultat.
 */
public final class ItemsSectionParser {

    private static final String ITEMS = "items";

    private ItemsSectionParser() {
    }

    /**
     * Lit et valide la section.
     *
     * @param root         racine de la configuration
     * @param availability items disponibles sur ce serveur
     * @return les items acceptes et disponibles, dans l'ordre du fichier
     */
    public static List<AcceptedItem> parse(ConfigNode root, ItemAvailability availability) {
        Set<ItemKey> seen = new HashSet<>();
        List<AcceptedItem> accepted = new ArrayList<>();
        for (ConfigNode entry : root.sectionList(ITEMS)) {
            Optional<ItemKey> key = entry.requiredString("key").flatMap(text -> parseKey(entry, text));
            Optional<BigDecimal> price = parsePrice(entry);
            Optional<String> category = entry.optionalMatching("category", IDENTIFIER, IDENTIFIER_HINT);
            Optional<String> displayName = entry.optionalText("display-name");
            if (key.isEmpty()) {
                continue;
            }
            if (!seen.add(key.get())) {
                entry.error("key", ConsoleText.tr("item '{0}' deja declare plus haut", key.get()));
            } else if (price.isPresent() && isAvailable(entry, key.get(), availability)) {
                accepted.add(new AcceptedItem(key.get(), price.get(), category, displayName));
            }
        }
        if (accepted.isEmpty()) {
            root.warning(ITEMS, ConsoleText.tr("aucun item accepte : aucun depot ne sera possible"));
        }
        return List.copyOf(accepted);
    }

    private static Optional<ItemKey> parseKey(ConfigNode entry, String text) {
        try {
            return Optional.of(ItemKey.parse(text));
        } catch (IllegalArgumentException e) {
            entry.error("key",
                    ConsoleText.tr("cle d'item invalide '{0}' (attendu namespace:id en minuscules, par exemple minecraft:stone)",
                            text));
            return Optional.empty();
        }
    }

    private static Optional<BigDecimal> parsePrice(ConfigNode entry) {
        Optional<BigDecimal> price = entry.requiredDecimal("price");
        if (price.isPresent() && price.get().signum() < 0) {
            entry.error("price", ConsoleText.tr("le prix doit etre positif ou nul (recu : {0})",
                    price.get().toPlainString()));
            return Optional.empty();
        }
        return price;
    }

    private static boolean isAvailable(ConfigNode entry, ItemKey key, ItemAvailability availability) {
        boolean vanilla = key.namespace().equals(ItemKey.MINECRAFT);
        if (!vanilla && !availability.providerPresent(key.namespace())) {
            entry.warning("key", ConsoleText.tr("fournisseur d'items '{0}' absent du serveur : item '{1}' ignore",
                    key.namespace(), key));
            return false;
        }
        if (!availability.exists(key)) {
            entry.warning("key", ConsoleText.tr("{0} : '{1}' ignore",
                    (vanilla ? "item inconnu dans cette version du serveur" : "item inconnu du fournisseur"), key));
            return false;
        }
        return true;
    }
}
