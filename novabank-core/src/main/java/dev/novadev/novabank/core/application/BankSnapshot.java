package dev.novadev.novabank.core.application;

import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.core.bank.Bank;
import dev.novadev.novabank.core.ledger.BankLedger;
import dev.novadev.novabank.core.money.Money;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Etat d'une banque pour l'affichage : valeur, stock et valeur de chaque ligne.
 *
 * @param owner      entite proprietaire
 * @param exists     {@code false} si la banque n'a encore jamais servi (creation paresseuse)
 * @param value      valeur courante
 * @param stock      quantite par item, items tries par cle, sans ligne vide
 * @param lineValues valeur courante par item du stock (absente : zero)
 */
public record BankSnapshot(EntityRef owner, boolean exists, BigDecimal value, Map<ItemKey, Long> stock,
                           Map<ItemKey, BigDecimal> lineValues) {

    /**
     * Fige le stock en conservant son ordre.
     *
     * @throws NullPointerException si un champ est nul
     */
    public BankSnapshot {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(value, "value");
        stock = Collections.unmodifiableMap(new LinkedHashMap<>(stock));
        lineValues = Map.copyOf(lineValues);
    }

    /**
     * @param item item du stock
     * @return la valeur de sa ligne (zero si inconnue)
     */
    public BigDecimal lineValue(ItemKey item) {
        return lineValues.getOrDefault(item, Money.ZERO);
    }

    /**
     * A appeler sur l'executeur IO (lecture du catalogue courant).
     *
     * @param owner  entite proprietaire
     * @param bank   banque lue, ou vide si elle n'existe pas encore
     * @param ledger valorisation au catalogue courant
     * @return l'etat a afficher
     */
    static BankSnapshot of(EntityRef owner, Optional<Bank> bank, BankLedger ledger) {
        if (bank.isEmpty()) {
            return new BankSnapshot(owner, false, Money.ZERO, Map.of(), Map.of());
        }
        Map<ItemKey, Long> stock = new LinkedHashMap<>();
        bank.get().lines().entrySet().stream()
                .filter(entry -> entry.getValue().quantity() > 0)
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ItemKey::toString)))
                .forEach(entry -> stock.put(entry.getKey(), entry.getValue().quantity()));
        Map<ItemKey, BigDecimal> values = new LinkedHashMap<>(ledger.lineValues(bank.get()));
        values.keySet().retainAll(stock.keySet());
        return new BankSnapshot(owner, true, ledger.valueOf(bank.get()), stock, values);
    }
}
