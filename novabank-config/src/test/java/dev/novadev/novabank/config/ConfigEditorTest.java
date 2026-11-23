package dev.novadev.novabank.config;

import static dev.novadev.novabank.config.TestConfigs.load;
import static dev.novadev.novabank.config.TestConfigs.minimal;
import static dev.novadev.novabank.config.TestConfigs.minimalWith;
import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.config.validation.ValidationIssue;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ConfigEditorTest {

    private final ConfigEditor editor = new ConfigEditor(new NovaBankConfigLoader(FakeItemAvailability.everything()));
    private final ConfigLoadResult current = load(minimal());

    private static void assertRefused(EditOutcome outcome, ConfigLoadResult current, String path, String reason) {
        assertThat(outcome.applied()).isFalse();
        assertThat(outcome.state()).isSameAs(current);
        assertThat(outcome.report().errors()).singleElement().satisfies(issue -> {
            assertThat(issue.path()).isEqualTo(path);
            assertThat(issue.message()).contains(reason);
        });
    }

    @Test
    void modification_valide_appliquee_avec_conversion_du_texte_saisi() {
        EditOutcome outcome = editor.edit(current, "limits.cooldown-ms", "1000");
        assertThat(outcome.applied()).isTrue();
        assertThat(outcome.state().config().orElseThrow().limits().cooldownMs()).isEqualTo(1000);
        assertThat(outcome.state().tree()).containsKey("limits");
        assertThat(current.config().orElseThrow().limits().cooldownMs()).isEqualTo(500);
    }

    @Test
    void modifications_successives_et_section_creee() {
        EditOutcome first = editor.edit(current, "banks.entity-types", List.of("player", "island"));
        EditOutcome second = editor.edit(first.state(), "banks.default-priority", List.of("island", "player"));
        assertThat(second.applied()).isTrue();
        assertThat(second.state().config().orElseThrow().banks().defaultPriority()).containsExactly("island", "player");
    }

    @Test
    void valeur_nulle_revient_a_la_valeur_par_defaut() {
        ConfigLoadResult edited = editor.edit(current, "limits.cooldown-ms", 10).state();
        EditOutcome outcome = editor.edit(edited, "limits.cooldown-ms", null);
        assertThat(outcome.applied()).isTrue();
        assertThat(outcome.state().config().orElseThrow().limits().cooldownMs()).isEqualTo(500);
    }

    @Test
    void modification_qui_invalide_la_configuration_refusee_et_config_inchangee() {
        EditOutcome outcome = editor.edit(current, "limits.max-quantity-per-operation", 0);
        assertRefused(outcome, current, "limits.max-quantity-per-operation", "superieur ou egal a 1");
    }

    @Test
    void regle_inter_sections_appliquee_en_jeu() {
        assertRefused(editor.edit(current, "redis.enabled", true), current, "redis.enabled", "mono-serveur");
    }

    @ParameterizedTest
    @ValueSource(strings = {"storage.sql.password", "storage.sql.username", "storage.mongodb.uri", "redis.password",
            "redis.password.sous-cle", "storage.sql", "storage", "redis", "storage.mongodb"})
    void chemin_secret_ou_contenant_un_secret_refuse(String path) {
        assertRefused(editor.edit(current, path, "valeur"), current, path, "uniquement dans config.yml");
    }

    @ParameterizedTest
    @ValueSource(strings = {"limits.inconnu", "inconnu", "leaderboards.faction.size", "Limits.cooldown-ms"})
    void chemin_inconnu_refuse(String path) {
        assertRefused(editor.edit(current, path, 1), current, path, "chemin inconnu");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "limits..cooldown-ms", "items[0].price", ".limits"})
    void chemin_mal_forme_refuse(String path) {
        assertRefused(editor.edit(current, path, 1), current, path, "chemin invalide");
    }

    @Test
    void niveau_intermediaire_qui_n_est_pas_une_section_refuse() {
        ConfigLoadResult withValue = editor.edit(current, "limits.cooldown-ms", 10).state();
        assertRefused(editor.edit(withValue, "limits.cooldown-ms.x", 1), withValue, "limits.cooldown-ms.x",
                "n'est pas une section");
    }

    @Test
    void version_du_schema_non_modifiable() {
        assertRefused(editor.edit(current, "config-version", 1), current, "config-version", "non modifiable");
    }

    @Test
    void liste_d_items_remplacee_en_entier() {
        EditOutcome outcome = editor.edit(current, "items",
                List.of(Map.of("key", "minecraft:dirt", "price", "0.5")));
        assertThat(outcome.applied()).isTrue();
        assertThat(outcome.state().config().orElseThrow().items()).singleElement()
                .satisfies(item -> assertThat(item.key().toString()).isEqualTo("minecraft:dirt"));
    }

    @Test
    void avertissements_de_la_nouvelle_configuration_transmis() {
        EditOutcome outcome = editor.edit(current, "storage.drivers.verify-sha256", false);
        assertThat(outcome.applied()).isTrue();
        assertThat(outcome.report().warnings()).extracting(ValidationIssue::path)
                .containsExactly("storage.drivers.verify-sha256");
    }

    @Test
    void edition_depuis_un_etat_charge_avec_options_libres() {
        ConfigLoadResult withOptions = load(minimalWith("storage.sql.options", Map.of("useSSL", "true")));
        assertThat(editor.edit(withOptions, "storage.sql.options.useSSL", "false").applied()).isTrue();
    }
}
