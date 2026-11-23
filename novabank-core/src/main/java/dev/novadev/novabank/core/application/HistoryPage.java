package dev.novadev.novabank.core.application;

import dev.novadev.novabank.core.journal.OperationRecord;
import dev.novadev.novabank.core.text.ConsoleText;
import java.util.List;

/**
 * Page du journal d'une banque.
 *
 * @param page    numero de page, a partir de 1
 * @param entries entrees, de la plus recente a la plus ancienne
 * @param hasNext {@code true} s'il existe une page suivante
 */
public record HistoryPage(int page, List<OperationRecord> entries, boolean hasNext) {

    /**
     * @throws IllegalArgumentException si le numero de page n'est pas positif
     */
    public HistoryPage {
        if (page < 1) {
            throw new IllegalArgumentException(ConsoleText.tr("Page invalide : {0}", page));
        }
        entries = List.copyOf(entries);
    }
}
