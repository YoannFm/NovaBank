package dev.novadev.novabank.config;

import static dev.novadev.novabank.config.TestConfigs.errorPaths;
import static dev.novadev.novabank.config.TestConfigs.load;
import static dev.novadev.novabank.config.TestConfigs.loadValid;
import static dev.novadev.novabank.config.TestConfigs.minimal;
import static dev.novadev.novabank.config.TestConfigs.minimalWith;
import static dev.novadev.novabank.config.TestConfigs.singleError;
import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.config.model.CommandLabel;
import dev.novadev.novabank.config.model.SecuritySettings;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Sections {@code commands}, {@code security} et cle {@code language}.
 */
class CommandsSecurityLanguageTest {

    @Test
    void valeurs_par_defaut() {
        NovaBankConfig config = loadValid(minimal());
        assertThat(config.commands().player()).isEqualTo(new CommandLabel("bank", List.of("banque")));
        assertThat(config.commands().admin()).isEqualTo(new CommandLabel("novabank", List.of()));
        assertThat(config.commands().hosts()).containsEntry("superiorskyblock2", new CommandLabel("bank", List.of("banque")))
                .containsEntry("towny", new CommandLabel("bank", List.of("banque")))
                .containsEntry("iridiumskyblock", new CommandLabel("coffre", List.of("items")))
                .containsEntry("simpleclans", new CommandLabel("coffre", List.of("items")))
                .containsEntry("lands", new CommandLabel("landbank", List.of()))
                .containsEntry("griefprevention", new CommandLabel("claimbank", List.of()))
                .containsEntry("worldguard", new CommandLabel("regionbank", List.of())).hasSize(8);
        assertThat(config.security()).isEqualTo(new SecuritySettings(true, 10_000L));
        assertThat(config.language()).isEqualTo("fr");
    }

    @Test
    void noms_et_alias_configurables_alias_normalises() {
        NovaBankConfig config = loadValid(minimalWith("commands", Map.of(
                "player", Map.of("name", "coffre", "aliases", List.of("Vault", "cf")),
                "admin", Map.of("name", "nbadmin", "aliases", List.of("nba")))));
        assertThat(config.commands().player()).isEqualTo(new CommandLabel("coffre", List.of("vault", "cf")));
        assertThat(config.commands().admin()).isEqualTo(new CommandLabel("nbadmin", List.of("nba")));
    }

    @Test
    void sous_commande_de_chaque_hote_configurable() {
        NovaBankConfig config = loadValid(minimalWith("commands", Map.of(
                "hosts", Map.of("superiorskyblock2", Map.of("name", "coffre", "aliases", List.of("Tresor")),
                        "lands", Map.of("name", "banqueterre")))));
        assertThat(config.commands().hosts().get("superiorskyblock2")).isEqualTo(new CommandLabel("coffre",
                List.of("tresor")));
        assertThat(config.commands().hosts().get("lands")).isEqualTo(new CommandLabel("banqueterre", List.of()));
        assertThat(config.commands().player().name()).as("une sous-commande peut reprendre le nom /bank").isEqualTo("bank");
        singleError(minimalWith("commands.hosts.lands.name", "bank"), "commands.hosts.lands.name");
        singleError(minimalWith("commands.hosts.towny.name", "ma banque"), "commands.hosts.towny.name");
    }

    @Test
    void noms_invalides_ou_en_double_refuses() {
        singleError(minimalWith("commands.player.name", "ma banque"), "commands.player.name");
        singleError(minimalWith("commands.admin.name", "bank"), "commands.admin.name");
        singleError(minimalWith("commands.admin.aliases", List.of("banque")), "commands.admin.aliases[0]");
        singleError(minimalWith("commands.player.aliases", List.of("b/q")), "commands.player.aliases[0]");
    }

    @Test
    void sauvegarde_des_donnees_joueur_desactivable() {
        assertThat(loadValid(minimalWith("security.save-player-data", false)).security().savePlayerData()).isFalse();
        singleError(minimalWith("security.save-player-data", "peut-etre"), "security.save-player-data");
    }

    @Test
    void delai_d_arret_configurable_et_borne() {
        assertThat(loadValid(minimalWith("security.shutdown-drain-timeout-ms", 0)).security().shutdownDrainTimeoutMs())
                .isZero();
        assertThat(loadValid(minimalWith("security.shutdown-drain-timeout-ms", 60_000)).security()
                .shutdownDrainTimeoutMs()).isEqualTo(60_000L);
        singleError(minimalWith("security.shutdown-drain-timeout-ms", 60_001), "security.shutdown-drain-timeout-ms");
        singleError(minimalWith("security.shutdown-drain-timeout-ms", -1), "security.shutdown-drain-timeout-ms");
    }

    @Test
    void langue_lue_et_validee() {
        assertThat(loadValid(minimalWith("language", "en")).language()).isEqualTo("en");
        assertThat(loadValid(minimalWith("language", "pt_br")).language()).isEqualTo("pt_br");
        ConfigLoadResult result = load(minimalWith("language", "Francais"));
        assertThat(errorPaths(result)).containsExactly("language");
    }
}
