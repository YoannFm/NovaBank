package dev.novadev.novabank.bukkit.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.bukkit.testing.TestConfigs;
import dev.novadev.novabank.config.ConfigLoadResult;
import dev.novadev.novabank.config.NovaBankConfigLoader;
import dev.novadev.novabank.core.valuation.PriceCatalog;
import dev.novadev.novabank.core.access.RoleRights;
import java.math.BigDecimal;
import java.time.Duration;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ActiveConfigurationTest {

    private static final ItemKey DIAMOND_BLOCK = ItemKey.minecraft("diamond_block");

    @Test
    void un_rechargement_valide_remplace_le_catalogue_a_chaud() throws Exception {
        ActiveConfiguration active = new ActiveConfiguration(load(TestConfigs.defaultYaml()));
        PriceCatalog liveCatalog = active.catalog();
        assertThat(liveCatalog.unitPrice(DIAMOND_BLOCK)).contains(new BigDecimal("90"));

        ReloadOutcome outcome = active.apply(load(TestConfigs.replaceOnce(TestConfigs.defaultYaml(),
                "price: \"90\"", "price: \"95.5\"")));

        assertThat(outcome.applied()).isTrue();
        assertThat(outcome.restartRequired()).isFalse();
        assertThat(outcome.acceptedItems()).isEqualTo(5);
        assertThat(liveCatalog.unitPrice(DIAMOND_BLOCK)).contains(new BigDecimal("95.5"));
        assertThat(active.config().items()).hasSize(5);
    }

    @Test
    void un_rechargement_avec_erreur_ne_change_rien() throws Exception {
        ActiveConfiguration active = new ActiveConfiguration(load(TestConfigs.defaultYaml()));

        ReloadOutcome outcome = active.apply(load(TestConfigs.replaceOnce(TestConfigs.defaultYaml(),
                "price: \"90\"", "price: \"-1\"")));

        assertThat(outcome.applied()).isFalse();
        assertThat(outcome.report().errors()).singleElement()
                .satisfies(issue -> assertThat(issue.path()).isEqualTo("items[3].price"));
        assertThat(active.catalog().unitPrice(DIAMOND_BLOCK)).contains(new BigDecimal("90"));
    }

    @Test
    void un_changement_de_stockage_demande_un_redemarrage() throws Exception {
        ActiveConfiguration active = new ActiveConfiguration(load(TestConfigs.defaultYaml()));

        ReloadOutcome outcome = active.apply(load(TestConfigs.replaceOnce(TestConfigs.defaultYaml(),
                "file: data/novabank.db", "file: data/autre.db")));

        assertThat(outcome.applied()).isTrue();
        assertThat(outcome.restartRequired()).isTrue();
        assertThat(active.config().storage().sqlite().file()).isEqualTo("data/autre.db");
    }

    @Test
    void un_changement_de_commande_ou_de_politique_demande_un_redemarrage_et_les_regles_suivent() throws Exception {
        ActiveConfiguration active = new ActiveConfiguration(load(TestConfigs.defaultYaml()));
        assertThat(active.rules().cooldown()).isEqualTo(Duration.ofMillis(500));
        assertThat(active.rules().acceptedItems()).hasSize(5).contains(DIAMOND_BLOCK);
        assertThat(active.rules().rightsFor("player")).isEqualTo(RoleRights.DEFAULT);
        assertThat(active.rules().savePlayerData()).isTrue();

        ReloadOutcome renamed = active.apply(load(TestConfigs.replaceOnce(TestConfigs.defaultYaml(),
                "  player:\n    name: bank", "  player:\n    name: coffre")));
        ReloadOutcome faster = active.apply(load(TestConfigs.replaceOnce(TestConfigs.replaceOnce(
                TestConfigs.defaultYaml(), "  player:\n    name: bank", "  player:\n    name: coffre"), "cooldown-ms: 500",
                "cooldown-ms: 0")));
        ReloadOutcome policy = active.apply(load(TestConfigs.replaceOnce(TestConfigs.replaceOnce(
                TestConfigs.defaultYaml(), "  player:\n    name: bank", "  player:\n    name: coffre"),
                "undelivered-policy: PENDING_DELIVERY", "undelivered-policy: RETURN_TO_BANK")));

        assertThat(renamed.restartRequired()).isTrue();
        assertThat(faster.restartRequired()).isFalse();
        assertThat(policy.restartRequired()).isTrue();
        assertThat(active.rules().cooldown()).isEqualTo(Duration.ofMillis(500));
    }

    @Test
    void la_configuration_initiale_doit_etre_valide() throws Exception {
        ConfigLoadResult invalid = load(TestConfigs.replaceOnce(TestConfigs.defaultYaml(),
                "max-quantity-per-operation: 2304", "max-quantity-per-operation: 0"));

        assertThatThrownBy(() -> new ActiveConfiguration(invalid)).isInstanceOf(IllegalArgumentException.class);
    }

    private static ConfigLoadResult load(String text) throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        return new NovaBankConfigLoader(new BukkitItemAvailability(id -> true, name -> false))
                .load(YamlTrees.toMap(yaml));
    }
}
