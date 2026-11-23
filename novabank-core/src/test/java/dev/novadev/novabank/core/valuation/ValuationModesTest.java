package dev.novadev.novabank.core.valuation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.api.valuation.ValuationMode;
import dev.novadev.novabank.core.bank.Bank;
import dev.novadev.novabank.core.testing.MutablePriceCatalog;
import dev.novadev.novabank.core.testing.TestBankIds;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Differences voulues entre les modes DERIVED et ACCUMULATED.
 */
class ValuationModesTest {

    private static final ItemKey IRON = ItemKey.minecraft("iron_block");

    private final MutablePriceCatalog catalog = new MutablePriceCatalog().price(IRON, "1");
    private final StockAccounting accounting = new StockAccounting(catalog);

    @Test
    void en_mode_derive_un_changement_de_prix_se_repercute() {
        Bank bank = deposit(bank(ValuationMode.DERIVED), 10);

        catalog.price(IRON, "3");

        assertThat(accounting.bankValue(bank)).isEqualByComparingTo("30");
        assertThat(accounting.withdraw(bank, IRON, 5).valueDelta()).isEqualByComparingTo("-15");
    }

    @Test
    void en_mode_cumule_un_changement_de_prix_n_est_pas_retroactif() {
        Bank bank = deposit(bank(ValuationMode.ACCUMULATED), 10);

        catalog.price(IRON, "3");
        assertThat(accounting.bankValue(bank)).isEqualByComparingTo("10");

        bank = accounting.withdraw(bank, IRON, 5).bank();
        assertThat(accounting.bankValue(bank)).isEqualByComparingTo("5");

        bank = deposit(bank, 1);
        assertThat(accounting.bankValue(bank)).isEqualByComparingTo("8");
    }

    @Test
    void en_mode_cumule_les_arrondis_ne_laissent_aucun_residu() {
        catalog.price(IRON, "0.3333333333");
        Bank bank = deposit(bank(ValuationMode.ACCUMULATED), 3);
        assertThat(accounting.bankValue(bank)).isEqualByComparingTo("1");

        List<BigDecimal> deltas = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Movement movement = accounting.withdraw(bank, IRON, 1);
            deltas.add(movement.valueDelta());
            bank = movement.bank();
        }

        assertThat(deltas).extracting(BigDecimal::toPlainString).containsExactly("-0.3333", "-0.3334", "-0.3333");
        assertThat(accounting.bankValue(bank)).isZero();
        assertThat(deltas.stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("-1");
    }

    @Test
    void un_item_retire_du_catalogue_vaut_zero_en_derive_mais_garde_sa_valeur_en_cumule() {
        Bank derived = deposit(bank(ValuationMode.DERIVED), 4);
        Bank accumulated = deposit(bank(ValuationMode.ACCUMULATED), 4);

        catalog.remove(IRON);

        assertThat(accounting.bankValue(derived)).isZero();
        assertThat(accounting.bankValue(accumulated)).isEqualByComparingTo("4");
        assertThat(accounting.deposit(derived, IRON, 1)).isEmpty();
        assertThat(accounting.withdraw(derived, IRON, 4).bank().lines()).isEmpty();
    }

    @Test
    void un_prix_negatif_au_catalogue_est_une_erreur_de_configuration() {
        catalog.price(IRON, "-1");

        assertThatThrownBy(() -> accounting.deposit(bank(ValuationMode.DERIVED), IRON, 1))
                .isInstanceOf(IllegalStateException.class);
    }

    private Bank deposit(Bank bank, long quantity) {
        return accounting.deposit(bank, IRON, quantity).orElseThrow().bank();
    }

    private static Bank bank(ValuationMode mode) {
        return Bank.open(TestBankIds.random(), new EntityRef(EntityRef.FACTION, "north"), mode);
    }
}
