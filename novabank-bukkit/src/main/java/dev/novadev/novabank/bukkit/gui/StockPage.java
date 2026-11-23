package dev.novadev.novabank.bukkit.gui;

import dev.novadev.novabank.core.application.BankSnapshot;
import dev.novadev.novabank.core.text.ConsoleText;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Page du stock affichee. La page demandee est ramenee dans les bornes (le stock peut
 * avoir diminue depuis l'affichage precedent).
 *
 * @param entries   lignes de la page
 * @param page      index de la page, a partir de 0
 * @param pageCount nombre de pages, 1 au minimum (banque vide)
 */
public record StockPage(List<StockEntry> entries, int page, int pageCount) {

    /**
     * Fige les lignes.
     */
    public StockPage {
        entries = List.copyOf(entries);
    }

    /**
     * @param snapshot  etat de la banque
     * @param requested page demandee
     * @param pageSize  lignes par page, positif
     * @return la page
     */
    public static StockPage of(BankSnapshot snapshot, int requested, int pageSize) {
        if (pageSize < 1) {
            throw new IllegalArgumentException(ConsoleText.tr("Taille de page invalide : {0}", pageSize));
        }
        List<StockEntry> all = new ArrayList<>();
        snapshot.stock().forEach((item, quantity) -> all.add(new StockEntry(item, quantity,
                snapshot.lineValue(item))));
        int pageCount = Math.max(1, (all.size() + pageSize - 1) / pageSize);
        int page = Math.max(0, Math.min(requested, pageCount - 1));
        int from = page * pageSize;
        return new StockPage(all.subList(Math.min(from, all.size()), Math.min(from + pageSize, all.size())), page,
                pageCount);
    }

    /**
     * @param index index dans la page
     * @return la ligne a cet index
     */
    public Optional<StockEntry> at(int index) {
        return index >= 0 && index < entries.size() ? Optional.of(entries.get(index)) : Optional.empty();
    }

    /**
     * @return {@code true} s'il existe une page precedente
     */
    public boolean hasPrevious() {
        return page > 0;
    }

    /**
     * @return {@code true} s'il existe une page suivante
     */
    public boolean hasNext() {
        return page < pageCount - 1;
    }
}
