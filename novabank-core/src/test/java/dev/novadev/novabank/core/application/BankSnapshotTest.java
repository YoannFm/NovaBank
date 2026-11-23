package dev.novadev.novabank.core.application;

import static dev.novadev.novabank.core.application.ApplicationFixture.DIAMOND;
import static dev.novadev.novabank.core.application.ApplicationFixture.PLAYER;
import static dev.novadev.novabank.core.application.ApplicationFixture.STONE;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Detail du stock affiche (commande info et GUI) : quantite et valeur de chaque ligne.
 */
class BankSnapshotTest {

    private final ApplicationFixture fixture = new ApplicationFixture();

    @Test
    void valeur_par_ligne_au_prix_courant_et_somme_egale_a_la_valeur_de_la_banque() {
        fixture.inventory.put(PLAYER, STONE, 30);
        fixture.inventory.put(PLAYER, DIAMOND, 3);
        fixture.depositOne(STONE, 30);
        fixture.depositOne(DIAMOND, 3);

        BankSnapshot snapshot = info();

        assertThat(snapshot.stock()).containsExactly(java.util.Map.entry(DIAMOND, 3L), java.util.Map.entry(STONE, 30L));
        assertThat(snapshot.lineValue(STONE)).isEqualByComparingTo("30");
        assertThat(snapshot.lineValue(DIAMOND)).isEqualByComparingTo("30");
        assertThat(snapshot.value()).isEqualByComparingTo("60");
        assertThat(snapshot.lineValue(ApplicationFixture.DIRT)).isEqualByComparingTo("0");
    }

    @Test
    void ligne_videe_absente_du_stock_et_des_valeurs() {
        fixture.inventory.put(PLAYER, STONE, 5);
        fixture.depositOne(STONE, 5);
        fixture.withdraw(STONE, Amount.of(5));

        BankSnapshot snapshot = info();

        assertThat(snapshot.exists()).isTrue();
        assertThat(snapshot.stock()).isEmpty();
        assertThat(snapshot.lineValues()).isEmpty();
    }

    @Test
    void banque_inexistante_vide_et_sans_valeur() {
        BankSnapshot snapshot = info();

        assertThat(snapshot.exists()).isFalse();
        assertThat(snapshot.value()).isEqualByComparingTo("0");
        assertThat(snapshot.lineValues()).isEmpty();
    }

    private BankSnapshot info() {
        List<BankView> views = new ArrayList<>();
        fixture.service.info(PLAYER, views::add);
        fixture.scheduler.runAll();
        return views.get(0).snapshot().orElseThrow();
    }
}
