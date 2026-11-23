package dev.novadev.novabank.config;

import static dev.novadev.novabank.config.TestConfigs.loadValid;
import static dev.novadev.novabank.config.TestConfigs.minimal;
import static dev.novadev.novabank.config.TestConfigs.minimalWith;
import static dev.novadev.novabank.config.TestConfigs.singleError;
import static dev.novadev.novabank.config.TestConfigs.with;
import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.config.model.ServerSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ServerSectionTest {

    @Test
    void valeurs_valides_lues() {
        NovaBankConfig config = loadValid(with(minimalWith("server.id", "survie-1"), "server.group", "reseau_a"));
        assertThat(config.server()).isEqualTo(new ServerSettings("survie-1", "reseau_a"));
    }

    @Test
    void section_absente_valeurs_par_defaut() {
        assertThat(loadValid(minimal()).server()).isEqualTo(new ServerSettings("main", "default"));
    }

    @Test
    void identifiant_numerique_lu_comme_texte() {
        assertThat(loadValid(minimalWith("server.id", 42)).server().id()).isEqualTo("42");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Main", "serveur 1", "lobby.1"})
    void identifiant_invalide_refuse(String id) {
        singleError(minimalWith("server.id", id), "server.id");
    }

    @Test
    void groupe_invalide_refuse() {
        singleError(minimalWith("server.group", "A B"), "server.group");
    }

    @Test
    void section_qui_n_est_pas_une_section_refusee() {
        assertThat(singleError(minimalWith("server", 5), "server").message()).contains("section attendue");
    }
}
