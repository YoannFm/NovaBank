package dev.novadev.novabank.core.application;

import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.core.text.ConsoleText;
import java.util.Objects;
import java.util.Optional;

/**
 * Items vises par un depot : un item precis, l'item tenu en main, ou tous les items
 * acceptes presents dans l'inventaire (une operation par item).
 *
 * @param kind nature du choix
 * @param item item precis, present uniquement pour {@link Kind#ITEM}
 */
public record ItemChoice(Kind kind, Optional<ItemKey> item) {

    /** Nature du choix. */
    public enum Kind {
        /** Un item precis. */
        ITEM,
        /** L'item tenu en main principale. */
        HELD,
        /** Tous les items acceptes de l'inventaire. */
        ALL_ACCEPTED
    }

    /**
     * @throws IllegalArgumentException si l'item est present sans etre attendu, ou l'inverse
     */
    public ItemChoice {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(item, "item");
        if ((kind == Kind.ITEM) != item.isPresent()) {
            throw new IllegalArgumentException(ConsoleText.tr("Choix d'item incoherent : {0} / {1}", kind, item));
        }
    }

    /**
     * @param item item precis
     * @return le choix
     */
    public static ItemChoice of(ItemKey item) {
        return new ItemChoice(Kind.ITEM, Optional.of(item));
    }

    /**
     * @return le choix de l'item tenu en main
     */
    public static ItemChoice held() {
        return new ItemChoice(Kind.HELD, Optional.empty());
    }

    /**
     * @return le choix de tous les items acceptes
     */
    public static ItemChoice allAccepted() {
        return new ItemChoice(Kind.ALL_ACCEPTED, Optional.empty());
    }
}
