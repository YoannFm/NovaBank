package dev.novadev.novabank.core.valuation;

import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.api.valuation.ValuationMode;
import dev.novadev.novabank.core.bank.Bank;
import dev.novadev.novabank.core.bank.StockLine;
import dev.novadev.novabank.core.testing.MutablePriceCatalog;
import dev.novadev.novabank.core.testing.TestBankIds;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Proprietes communes aux deux strategies de valeur, verifiees via {@link StockAccounting}.
 */
class ValuationStrategyContractTest {

    private static final ItemKey GOLD = ItemKey.minecraft("gold_block");

    private final MutablePriceCatalog catalog = new MutablePriceCatalog().price(GOLD, "3.25");
    private final StockAccounting accounting = new StockAccounting(catalog);

    @ParameterizedTest
    @EnumSource(ValuationMode.class)
    void la_strategie_annonce_son_mode(ValuationMode mode) {
        assertThat(ValuationStrategy.of(mode).mode()).isEqualTo(mode);
    }

    @ParameterizedTest
    @EnumSource(ValuationMode.class)
    void une_ligne_vide_ne_vaut_rien(ValuationMode mode) {
        assertThat(ValuationStrategy.of(mode).lineValue(StockLine.EMPTY, Optional.of(BigDecimal.TEN))).isZero();
        assertThat(accounting.bankValue(emptyBank(mode))).isZero();
    }

    @ParameterizedTest
    @EnumSource(ValuationMode.class)
    void un_depot_vaut_quantite_comptee_fois_prix_courant(ValuationMode mode) {
        Movement movement = accounting.deposit(emptyBank(mode), GOLD, 4).orElseThrow();

        assertThat(movement.countedQuantity()).isEqualTo(4L);
        assertThat(movement.valueDelta()).isEqualByComparingTo("13");
        assertThat(accounting.bankValue(movement.bank())).isEqualByComparingTo("13");
    }

    @ParameterizedTest
    @EnumSource(ValuationMode.class)
    void tout_retirer_ramene_la_valeur_a_zero_et_oublie_la_ligne(ValuationMode mode) {
        Bank bank = accounting.deposit(emptyBank(mode), GOLD, 7).orElseThrow().bank();

        Movement movement = accounting.withdraw(bank, GOLD, 7);

        assertThat(movement.valueDelta()).isEqualByComparingTo("-22.75");
        assertThat(movement.bank().lines()).isEmpty();
        assertThat(accounting.bankValue(movement.bank())).isZero();
    }

    @ParameterizedTest
    @EnumSource(ValuationMode.class)
    void la_variation_annoncee_est_toujours_l_ecart_de_valeur_de_banque(ValuationMode mode) {
        Random random = new Random(42L);
        Bank bank = emptyBank(mode);
        for (int i = 0; i < 2_000; i++) {
            BigDecimal before = accounting.bankValue(bank);
            Movement movement = randomMovement(random, bank);
            bank = movement.bank();
            StockLine line = bank.line(GOLD);

            assertThat(accounting.bankValue(bank)).isNotNegative()
                    .isEqualByComparingTo(before.add(movement.valueDelta()));
            if (line.countedQuantity() == 0L) {
                assertThat(accounting.bankValue(bank)).isZero();
            }
        }
    }

    private Movement randomMovement(Random random, Bank bank) {
        long stock = bank.line(GOLD).quantity();
        int choice = random.nextInt(10);
        if (choice == 0) {
            return accounting.reset(bank);
        }
        if (choice < 5 || stock == 0L) {
            return accounting.deposit(bank, GOLD, 1L + random.nextInt(20)).orElseThrow();
        }
        return accounting.withdraw(bank, GOLD, 1L + (long) random.nextInt((int) Math.min(stock, 30L)));
    }

    private static Bank emptyBank(ValuationMode mode) {
        return Bank.open(TestBankIds.random(), new EntityRef(EntityRef.ISLAND, "42"), mode);
    }
}
