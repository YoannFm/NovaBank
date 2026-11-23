package dev.novadev.novabank.config;

import static dev.novadev.novabank.config.TestConfigs.errorPaths;
import static dev.novadev.novabank.config.TestConfigs.load;
import static dev.novadev.novabank.config.TestConfigs.loadValid;
import static dev.novadev.novabank.config.TestConfigs.minimal;
import static dev.novadev.novabank.config.TestConfigs.minimalWith;
import static dev.novadev.novabank.config.TestConfigs.singleError;
import static dev.novadev.novabank.config.TestConfigs.warningPaths;
import static dev.novadev.novabank.config.TestConfigs.with;
import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.api.entity.BankRole;
import dev.novadev.novabank.api.valuation.ValuationMode;
import dev.novadev.novabank.config.model.BankSettings;
import dev.novadev.novabank.core.access.RoleRights;
import dev.novadev.novabank.core.delivery.UndeliveredPolicy;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BanksSectionTest {

    @Test
    void valeurs_valides_lues_et_types_normalises() {
        Map<String, Object> tree = minimalWith("banks", Map.of(
                "entity-types", List.of("player", "Island", "faction"),
                "default-priority", List.of("faction", "island"),
                "valuation-mode", "accumulated",
                "undelivered-policy", "return-to-bank"));
        BankSettings banks = loadValid(tree).banks();
        assertThat(banks.entityTypes()).containsExactly("player", "island", "faction");
        assertThat(banks.defaultPriority()).containsExactly("faction", "island");
        assertThat(banks.valuationMode()).isEqualTo(ValuationMode.ACCUMULATED);
        assertThat(banks.undeliveredPolicy()).isEqualTo(UndeliveredPolicy.RETURN_TO_BANK);
    }

    @Test
    void section_absente_valeurs_par_defaut() {
        BankSettings banks = loadValid(minimal()).banks();
        assertThat(banks.entityTypes()).containsExactly("player");
        assertThat(banks.defaultPriority()).containsExactly("player");
        assertThat(banks.valuationMode()).isEqualTo(ValuationMode.DERIVED);
        assertThat(banks.undeliveredPolicy()).isEqualTo(UndeliveredPolicy.PENDING_DELIVERY);
    }

    @Test
    void priorite_par_defaut_suit_l_ordre_des_types() {
        BankSettings banks = loadValid(minimalWith("banks.entity-types", List.of("town", "player"))).banks();
        assertThat(banks.defaultPriority()).containsExactly("town", "player");
    }

    @Test
    void priorite_referencant_un_type_non_active_refusee() {
        Map<String, Object> tree = with(minimalWith("banks.entity-types", List.of("player", "island")),
                "banks.default-priority", List.of("island", "faction"));
        assertThat(singleError(tree, "banks.default-priority[1]").message()).contains("faction");
    }

    @Test
    void priorite_en_double_refusee() {
        singleError(minimalWith("banks.default-priority", List.of("player", "PLAYER")), "banks.default-priority[1]");
    }

    @Test
    void aucun_type_active_refuse() {
        singleError(minimalWith("banks.entity-types", List.of()), "banks.entity-types");
    }

    @Test
    void type_invalide_refuse_et_doublon_averti() {
        ConfigLoadResult result = load(with(minimalWith("banks.entity-types", List.of("is land", "player", "Player")),
                "banks.default-priority", List.of("player")));
        assertThat(errorPaths(result)).containsExactly("banks.entity-types[0]");
        assertThat(warningPaths(result)).containsExactly("banks.entity-types[2]");
    }

    @Test
    void element_non_texte_refuse() {
        singleError(minimalWith("banks.entity-types", List.of("player", Map.of("a", 1))), "banks.entity-types[1]");
        singleError(minimalWith("banks.entity-types", "player"), "banks.entity-types");
    }

    @Test
    void modes_inconnus_refuses() {
        singleError(minimalWith("banks.valuation-mode", "fixed"), "banks.valuation-mode");
        singleError(minimalWith("banks.undelivered-policy", "drop"), "banks.undelivered-policy");
    }

    @Test
    void droits_par_defaut_pour_chaque_type_active() {
        BankSettings banks = loadValid(minimalWith("banks.entity-types", List.of("player", "island"))).banks();
        assertThat(banks.roles()).containsOnlyKeys("player", "island");
        assertThat(banks.roles().values()).containsOnly(RoleRights.DEFAULT);
    }

    @Test
    void droits_lus_par_type_avec_roles_insensibles_a_la_casse_et_liste_vide() {
        Map<String, Object> tree = with(minimalWith("banks.entity-types", List.of("player", "island")),
                "banks.roles", Map.of("island", Map.of("deposit", List.of("OWNER", "member"), "withdraw", List.of())));
        RoleRights island = loadValid(tree).banks().roles().get("island");
        assertThat(island.deposit()).containsExactlyInAnyOrder(BankRole.OWNER, BankRole.MEMBER);
        assertThat(island.withdraw()).isEmpty();
    }

    @Test
    void role_inconnu_refuse_et_type_non_active_ignore() {
        singleError(minimalWith("banks.roles", Map.of("player", Map.of("withdraw", List.of("owner", "chef")))),
                "banks.roles.player.withdraw[1]");
        ConfigLoadResult result = load(minimalWith("banks.roles", Map.of("faction", Map.of("deposit", List.of()))));
        assertThat(result.config()).isPresent();
        assertThat(warningPaths(result)).containsExactly("banks.roles.faction");
    }
}
