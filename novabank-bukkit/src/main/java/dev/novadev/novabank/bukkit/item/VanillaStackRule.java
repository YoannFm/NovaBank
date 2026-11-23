package dev.novadev.novabank.bukkit.item;

import dev.novadev.novabank.api.item.ItemKey;
import java.util.Objects;
import java.util.Optional;

/**
 * Regle d'identite des items vanilla (D12), sans dependance serveur : une pile est
 * acceptee uniquement si son type est un item {@code minecraft:}, sa quantite positive et
 * sa meta celle par defaut (ni nom, ni lore, ni enchantement, ni durabilite modifiee).
 */
public final class VanillaStackRule {

    private VanillaStackRule() {
    }

    /**
     * Faits extraits d'une pile.
     *
     * @param typeKey     cle du materiau, par exemple {@code minecraft:stone}
     * @param amount      quantite de la pile
     * @param air         {@code true} pour une case vide
     * @param defaultMeta {@code true} si la pile n'a aucune meta ou la meta par defaut de son type
     */
    public record StackFacts(String typeKey, int amount, boolean air, boolean defaultMeta) {

        /**
         * @throws NullPointerException si la cle est nulle
         */
        public StackFacts {
            Objects.requireNonNull(typeKey, "typeKey");
        }
    }

    /**
     * @param facts faits de la pile
     * @return la cle de l'item si la pile est acceptee, sinon vide
     */
    public static Optional<ItemKey> identify(StackFacts facts) {
        if (facts.air() || facts.amount() <= 0 || !facts.defaultMeta()) {
            return Optional.empty();
        }
        try {
            ItemKey key = ItemKey.parse(facts.typeKey());
            return ItemKey.MINECRAFT.equals(key.namespace()) ? Optional.of(key) : Optional.empty();
        } catch (IllegalArgumentException notAKey) {
            return Optional.empty();
        }
    }
}
