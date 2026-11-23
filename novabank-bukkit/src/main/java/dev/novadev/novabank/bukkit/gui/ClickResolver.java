package dev.novadev.novabank.bukkit.gui;

import dev.novadev.novabank.config.model.GuiButton;
import java.util.Optional;

/**
 * Traduit un clic en action, sans dependance serveur. Sur une ligne de stock : gauche =
 * une pile (taille maximale de l'item), droit = 1, majuscule = tout ce qui tient dans
 * l'inventaire. Toute quantite est bornee par la place libre de l'inventaire (sinon le
 * surplus partirait en livraison differee : stock debite, rien dans l'inventaire), par le
 * stock affiche et par la limite par operation ; sans place, le joueur est prevenu et rien
 * n'est retire. La couche application reverifie tout (stock, droits, limites).
 */
public final class ClickResolver {

    private ClickResolver() {
    }

    /**
     * @param layout          disposition
     * @param page            page affichee
     * @param slot            case brute cliquee dans le coffre
     * @param click           nature du clic
     * @param room            place dans l'inventaire du joueur
     * @param maxPerOperation quantite maximale par operation
     * @return l'action
     */
    public static MenuAction resolve(MenuLayout layout, StockPage page, int slot, MenuClick click, InventoryRoom room,
                                     long maxPerOperation) {
        if (click == MenuClick.OTHER) {
            return MenuAction.NONE;
        }
        Optional<StockEntry> entry = layout.stockIndex(slot).flatMap(page::at);
        if (entry.isPresent()) {
            return withdraw(entry.get(), click, room, maxPerOperation);
        }
        return layout.button(slot).map(button -> button(button, page)).orElse(MenuAction.NONE);
    }

    private static MenuAction withdraw(StockEntry entry, MenuClick click, InventoryRoom room, long maxPerOperation) {
        long wanted = switch (click) {
            case LEFT -> Math.max(1, room.maxStackSize(entry.item()));
            case RIGHT -> 1L;
            default -> Long.MAX_VALUE;
        };
        long quantity = Math.min(Math.min(Math.min(wanted, room.room(entry.item())), entry.quantity()), maxPerOperation);
        return quantity < 1 ? new MenuAction.NoRoom(entry.item()) : new MenuAction.Withdraw(entry.item(), quantity);
    }

    private static MenuAction button(GuiButton button, StockPage page) {
        return switch (button) {
            case PREVIOUS -> page.hasPrevious() ? new MenuAction.ShowPage(page.page() - 1) : MenuAction.NONE;
            case NEXT -> page.hasNext() ? new MenuAction.ShowPage(page.page() + 1) : MenuAction.NONE;
            case DEPOSIT_ALL -> new MenuAction.DepositAll();
            case CLOSE -> new MenuAction.Close();
            case INFO -> new MenuAction.ShowPrices();
        };
    }
}
