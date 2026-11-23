package dev.novadev.novabank.bukkit.command;

import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.core.application.Amount;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Lecture des arguments de commande, sans dependance serveur.
 */
public final class CommandArguments {

    /** Mot-cle "tout". */
    public static final String ALL = "all";

    private CommandArguments() {
    }

    /**
     * Item saisi : {@code stone} (vanilla implicite) ou {@code namespace:id}, insensible a la casse.
     *
     * @param text saisie
     * @return la cle, ou vide si la saisie n'est pas une cle valide
     */
    public static Optional<ItemKey> item(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        try {
            return Optional.of(lower.indexOf(':') < 0 ? ItemKey.minecraft(lower) : ItemKey.parse(lower));
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }

    /**
     * Forme courte d'une cle pour l'affichage et la completion : l'identifiant seul pour un
     * item vanilla, la cle complete sinon.
     *
     * @param key cle
     * @return la forme courte
     */
    public static String shortName(ItemKey key) {
        return ItemKey.MINECRAFT.equals(key.namespace()) ? key.id() : key.toString();
    }

    /**
     * @param text saisie : entier positif ou {@value #ALL}
     * @return la quantite, ou vide si la saisie est invalide
     */
    public static Optional<Amount> amount(String text) {
        if (ALL.equalsIgnoreCase(text)) {
            return Optional.of(Amount.everything());
        }
        try {
            long value = Long.parseLong(text);
            return value > 0 ? Optional.of(Amount.of(value)) : Optional.empty();
        } catch (NumberFormatException invalid) {
            return Optional.empty();
        }
    }

    /**
     * @param text saisie
     * @return le numero de page (1 minimum), ou vide si la saisie est invalide
     */
    public static Optional<Integer> page(String text) {
        try {
            int page = Integer.parseInt(text);
            return page > 0 ? Optional.of(page) : Optional.empty();
        } catch (NumberFormatException invalid) {
            return Optional.empty();
        }
    }

    /**
     * @param candidates propositions possibles
     * @param prefix     saisie partielle
     * @return les propositions qui commencent par la saisie (casse ignoree), triees, sans doublon
     */
    public static List<String> matching(Collection<String> candidates, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return candidates.stream()
                .filter(candidate -> candidate.toLowerCase(Locale.ROOT).startsWith(lower))
                .distinct()
                .sorted()
                .toList();
    }
}
