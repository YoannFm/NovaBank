package dev.novadev.novabank.core.valuation;

import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.core.bank.Bank;
import dev.novadev.novabank.core.bank.StockLine;
import dev.novadev.novabank.core.money.Money;
import dev.novadev.novabank.core.text.ConsoleText;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Calcule les mouvements de stock et la valeur des banques, sans rien enregistrer.
 *
 * <p>Fonctions pures de l'etat de banque et du catalogue : sures a rappeler, ce que
 * les backends a verrou optimiste font en cas de conflit. La strategie de valeur est
 * choisie d'apres le mode de chaque banque.
 */
public final class StockAccounting {

    private final PriceCatalog catalog;

    /**
     * @param catalog catalogue de prix courant
     */
    public StockAccounting(PriceCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    /**
     * Valeur totale d'une banque, jamais negative.
     *
     * @param bank banque a valoriser
     * @return la somme des valeurs de ses lignes
     */
    public BigDecimal bankValue(Bank bank) {
        BigDecimal total = Money.ZERO;
        for (BigDecimal value : lineValues(bank).values()) {
            total = total.add(value);
        }
        return Money.atLeastZero(total);
    }

    /**
     * Valeur de chaque ligne d'une banque (affichage du detail du stock).
     *
     * @param bank banque a valoriser
     * @return la valeur par item, dans l'ordre des lignes de la banque
     */
    public Map<ItemKey, BigDecimal> lineValues(Bank bank) {
        ValuationStrategy strategy = ValuationStrategy.of(bank.valuationMode());
        Map<ItemKey, BigDecimal> values = new LinkedHashMap<>();
        for (Map.Entry<ItemKey, StockLine> entry : bank.lines().entrySet()) {
            values.put(entry.getKey(), strategy.lineValue(entry.getValue(), unitPrice(entry.getKey())));
        }
        return values;
    }

    /**
     * Depot : les unites sont comptees au-dela de la base, au prix courant.
     *
     * @param bank     banque cible
     * @param item     item depose
     * @param quantity quantite positive
     * @return le mouvement, ou vide si l'item n'est pas au catalogue
     * @throws ArithmeticException si le stock deborde
     */
    public Optional<Movement> deposit(Bank bank, ItemKey item, long quantity) {
        return unitPrice(item).map(price -> {
            StockLine before = bank.line(item);
            StockLine moved = before.plus(quantity);
            long counted = moved.countedQuantity() - before.countedQuantity();
            BigDecimal stored = strategy(bank).storedValueAfterCredit(before, Money.times(price, counted));
            return settle(bank, item, before, moved.withAccumulatedValue(stored), counted);
        });
    }

    /**
     * Retrait : les unites comptees sortent en premier.
     *
     * @param bank     banque source
     * @param item     item retire
     * @param quantity quantite, au plus le stock de la ligne
     * @return le mouvement
     * @throws IllegalArgumentException si le stock est insuffisant
     */
    public Movement withdraw(Bank bank, ItemKey item, long quantity) {
        StockLine before = bank.line(item);
        StockLine moved = before.minus(quantity);
        long counted = before.countedQuantity() - moved.countedQuantity();
        BigDecimal stored = strategy(bank).storedValueAfterDebit(before, counted);
        return settle(bank, item, before, moved.withAccumulatedValue(stored), counted);
    }

    /**
     * Restitution d'un retrait non livre, a l'identique de ce qui avait ete retire :
     * les unites non comptees sont rendues d'abord (sans valeur), puis les unites
     * comptees avec la valeur enregistree au retrait, sans recalcul au prix courant.
     *
     * <p>La base ne baisse jamais : si d'autres retraits ont creuse un deficit sous la
     * base entre-temps, les unites rendues le comblent d'abord et sont alors moins
     * comptees qu'au retrait (la valeur rendue suit au prorata). Choix anti-abus.
     *
     * @param bank            banque cible
     * @param item            item rendu
     * @param quantity        unites rendues
     * @param countedQuantity parmi elles, unites qui etaient comptees au retrait
     * @param recordedValue   valeur enregistree de ces unites comptees
     * @return le mouvement
     * @throws ArithmeticException si le stock deborde
     */
    public Movement restore(Bank bank, ItemKey item, long quantity, long countedQuantity, BigDecimal recordedValue) {
        StockLine before = bank.line(item);
        StockLine moved = before.plusUncounted(quantity - countedQuantity).plus(countedQuantity);
        long credited = moved.countedQuantity() - before.countedQuantity();
        BigDecimal value = Money.share(recordedValue, credited, countedQuantity);
        BigDecimal stored = strategy(bank).storedValueAfterCredit(before, value);
        return settle(bank, item, before, moved.withAccumulatedValue(stored), credited);
    }

    /**
     * Reinitialisation : toute la valeur disparait, le stock reste.
     *
     * @param bank banque a reinitialiser
     * @return le mouvement (unites comptees effacees, variation = -valeur avant)
     */
    public Movement reset(Bank bank) {
        Bank after = bank.reset();
        return new Movement(after, bank.countedQuantity(), bankValue(after).subtract(bankValue(bank)));
    }

    private Movement settle(Bank bank, ItemKey item, StockLine before, StockLine after, long counted) {
        ValuationStrategy strategy = strategy(bank);
        Optional<BigDecimal> price = unitPrice(item);
        BigDecimal delta = strategy.lineValue(after, price).subtract(strategy.lineValue(before, price));
        return new Movement(bank.withLine(item, after), counted, delta);
    }

    private static ValuationStrategy strategy(Bank bank) {
        return ValuationStrategy.of(bank.valuationMode());
    }

    private Optional<BigDecimal> unitPrice(ItemKey item) {
        Optional<BigDecimal> price = catalog.unitPrice(item);
        price.ifPresent(value -> {
            if (value.signum() < 0) {
                throw new IllegalStateException(ConsoleText.tr("Prix negatif au catalogue pour {0} : {1}",
                        item, value));
            }
        });
        return price;
    }
}
