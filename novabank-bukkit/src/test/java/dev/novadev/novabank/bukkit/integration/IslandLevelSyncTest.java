package dev.novadev.novabank.bukkit.integration;

import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.api.BankInfo;
import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.valuation.ValuationMode;
import dev.novadev.novabank.config.model.IslandLevelSettings;
import dev.novadev.novabank.config.model.IslandLevelSettings.Target;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class IslandLevelSyncTest {

    private static final EntityRef ISLAND = new EntityRef(EntityRef.ISLAND, "6f9619ff-8b86-d011-b42d-00c04fc964ff");

    /** Bonus de valeur suivi par les champs ; le bonus de niveau a les siens. */
    private static final class FakeIsland implements IslandLevelSync.IslandBonus {

        BigDecimal bonus = new BigDecimal("500");
        BigDecimal applied = BigDecimal.ZERO;
        BigDecimal levelBonus = BigDecimal.ZERO;
        BigDecimal levelApplied = BigDecimal.ZERO;
        int writes;

        @Override
        public BigDecimal applied(Target target) {
            return target == Target.WORTH ? applied : levelApplied;
        }

        @Override
        public BigDecimal bonus(Target target) {
            return target == Target.WORTH ? bonus : levelBonus;
        }

        @Override
        public void update(Target target, BigDecimal newBonus, BigDecimal newApplied) {
            if (target == Target.WORTH) {
                bonus = newBonus;
                applied = newApplied;
            } else {
                levelBonus = newBonus;
                levelApplied = newApplied;
            }
            writes++;
        }
    }

    private final FakeIsland island = new FakeIsland();
    private BigDecimal bankValue = new BigDecimal("100");
    private IslandLevelSettings settings = new IslandLevelSettings(true, Target.WORTH, BigDecimal.ONE);

    private final IslandLevelSync sync = new IslandLevelSync(() -> settings,
            bank -> CompletableFuture.completedFuture(Optional.of(new BankInfo(bank, ISLAND, ValuationMode.DERIVED,
                    bankValue, Map.of(), 1))),
            id -> id.equals(ISLAND.id()) ? Optional.of(island) : Optional.empty(), Runnable::run,
            Logger.getLogger("test"));

    @Test
    void la_valeur_de_la_banque_s_ajoute_sans_toucher_au_bonus_existant_et_se_resynchronise() {
        sync.sync(BankId.forOwner(ISLAND), ISLAND.id());
        assertThat(island.bonus).isEqualByComparingTo("600");
        assertThat(island.applied).isEqualByComparingTo("100");

        bankValue = new BigDecimal("250");
        sync.sync(BankId.forOwner(ISLAND), ISLAND.id());
        assertThat(island.bonus).isEqualByComparingTo("750");

        bankValue = BigDecimal.ZERO;
        sync.sync(BankId.forOwner(ISLAND), ISLAND.id());
        assertThat(island.bonus).isEqualByComparingTo("500");
    }

    @Test
    void sans_changement_rien_n_est_ecrit_et_le_coefficient_s_applique() {
        settings = new IslandLevelSettings(true, Target.LEVEL, new BigDecimal("0.5"));
        sync.sync(BankId.forOwner(ISLAND), ISLAND.id());
        sync.sync(BankId.forOwner(ISLAND), ISLAND.id());

        assertThat(island.writes).isEqualTo(1);
        assertThat(island.levelBonus).isEqualByComparingTo("50");
        assertThat(island.bonus).isEqualByComparingTo("500");
    }

    @Test
    void changer_apply_to_retire_la_part_de_novabank_de_l_ancien_bonus() {
        sync.sync(BankId.forOwner(ISLAND), ISLAND.id());
        assertThat(island.bonus).isEqualByComparingTo("600");

        settings = new IslandLevelSettings(true, Target.LEVEL, BigDecimal.ONE);
        sync.sync(BankId.forOwner(ISLAND), ISLAND.id());

        assertThat(island.bonus).isEqualByComparingTo("500");
        assertThat(island.applied).isZero();
        assertThat(island.levelBonus).isEqualByComparingTo("100");
    }

    @Test
    void desactive_ou_ile_inconnue_rien_ne_change() {
        settings = new IslandLevelSettings(false, Target.WORTH, BigDecimal.ONE);
        sync.sync(BankId.forOwner(ISLAND), ISLAND.id());
        assertThat(island.writes).isZero();

        settings = new IslandLevelSettings(true, Target.WORTH, BigDecimal.ONE);
        sync.sync(BankId.forOwner(ISLAND), "autre-ile");
        assertThat(island.writes).isZero();
    }
}
