package dev.novadev.novabank.bukkit.gui;

import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.core.text.ConsoleText;
import java.util.Objects;

/**
 * Action demandee par un clic dans la GUI.
 */
public sealed interface MenuAction {

    /** Aucune action. */
    MenuAction NONE = new Nothing();

    /**
     * Retrait d'une quantite d'un item.
     *
     * @param item     item
     * @param quantity quantite, positive
     */
    record Withdraw(ItemKey item, long quantity) implements MenuAction {

        /**
         * @throws IllegalArgumentException si la quantite n'est pas positive
         */
        public Withdraw {
            Objects.requireNonNull(item, "item");
            if (quantity < 1) {
                throw new IllegalArgumentException(ConsoleText.tr("Quantite invalide : {0}", quantity));
            }
        }
    }

    /** Depot de tous les items acceptes de l'inventaire. */
    record DepositAll() implements MenuAction {
    }

    /**
     * Affichage d'une autre page.
     *
     * @param page page a afficher
     */
    record ShowPage(int page) implements MenuAction {
    }

    /** Liste des items acceptes et de leur prix (bouton Information). */
    record ShowPrices() implements MenuAction {
    }

    /** Fermeture de la GUI. */
    record Close() implements MenuAction {
    }

    /**
     * Retrait impossible faute de place dans l'inventaire.
     *
     * @param item item vise
     */
    record NoRoom(ItemKey item) implements MenuAction {
    }

    /** Aucune action. */
    record Nothing() implements MenuAction {
    }
}
