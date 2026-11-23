package dev.novadev.novabank.core.valuation;

import dev.novadev.novabank.core.bank.Bank;
import dev.novadev.novabank.core.text.ConsoleText;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Effet calcule d'un mouvement de stock, avant enregistrement.
 *
 * @param bank            banque apres le mouvement (version inchangee)
 * @param countedQuantity unites comptees deplacees (valeur absolue)
 * @param valueDelta      variation de valeur de la banque, negative pour une sortie
 */
public record Movement(Bank bank, long countedQuantity, BigDecimal valueDelta) {

    /**
     * Construit un mouvement.
     *
     * @throws IllegalArgumentException si {@code countedQuantity} est negatif
     */
    public Movement {
        Objects.requireNonNull(bank, "bank");
        Objects.requireNonNull(valueDelta, "valueDelta");
        if (countedQuantity < 0) {
            throw new IllegalArgumentException(ConsoleText.tr("Unites comptees negatives : {0}", countedQuantity));
        }
    }
}
