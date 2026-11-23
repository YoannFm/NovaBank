package dev.novadev.novabank.config;

import static dev.novadev.novabank.config.TestConfigs.errorPaths;
import static dev.novadev.novabank.config.TestConfigs.load;
import static dev.novadev.novabank.config.TestConfigs.minimal;
import static dev.novadev.novabank.config.TestConfigs.minimalWith;
import static dev.novadev.novabank.config.TestConfigs.singleError;
import static dev.novadev.novabank.config.TestConfigs.warningPaths;
import static dev.novadev.novabank.config.TestConfigs.with;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.novadev.novabank.config.migration.ConfigMigrator;
import dev.novadev.novabank.config.validation.ValidationReport;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class NovaBankConfigLoaderTest {

    @Test
    void toutes_les_erreurs_de_toutes_les_sections_sont_accumulees() {
        Map<String, Object> tree = minimalWith("server.id", "Mauvais Id");
        tree = with(tree, "storage.sql.port", -1);
        tree = with(tree, "redis.enabled", true);
        tree = with(tree, "banks.default-priority", List.of("island"));
        tree = with(tree, "items", List.of(Map.of("key", "minecraft:stone", "price", "-2")));
        tree = with(tree, "limits.cooldown-ms", "vite");
        tree = with(tree, "leaderboards.player.size", 0);
        tree = with(tree, "diagnostics.debug", "peut-etre");
        ConfigLoadResult result = load(tree);
        assertThat(result.config()).isEmpty();
        assertThat(errorPaths(result)).containsExactlyInAnyOrder("server.id", "storage.sql.port", "redis.enabled",
                "banks.default-priority[0]", "items[0].price", "limits.cooldown-ms", "leaderboards.player.size",
                "diagnostics.debug");
    }

    @Test
    void configuration_vide_chargee_avec_les_valeurs_par_defaut_et_deux_avertissements() {
        ConfigLoadResult result = load(Map.of());
        assertThat(result.config()).isPresent();
        assertThat(errorPaths(result)).isEmpty();
        assertThat(warningPaths(result)).containsExactly("config-version", "items");
    }

    @Test
    void cles_inconnues_signalees_par_avertissement() {
        Map<String, Object> tree = with(minimalWith("storage.sql.hots", "x"), "inconnue", Map.of("a", 1));
        ConfigLoadResult result = load(with(tree, "items", List.of(Map.of("key", "minecraft:stone", "price", "1", "prise", 2))));
        assertThat(result.config()).isPresent();
        assertThat(warningPaths(result)).containsExactlyInAnyOrder("storage.sql.hots", "inconnue", "items[0].prise");
        assertThat(result.report().warnings()).allSatisfy(issue -> assertThat(issue.message()).contains("inconnue"));
    }

    @Test
    void cles_non_textuelles_converties() {
        Map<Object, Object> tree = new java.util.HashMap<>(minimal());
        tree.put(42, "x");
        assertThat(warningPaths(load(tree))).containsExactly("42");
    }

    @Test
    void version_de_schema_invalide_ou_trop_recente_bloque_la_lecture() {
        assertThat(singleError(minimalWith("config-version", ConfigMigrator.LATEST_VERSION + 1), "config-version")
                .message()).contains("plus recente");
        singleError(minimalWith("config-version", 0), "config-version");
        singleError(minimalWith("config-version", "un"), "config-version");
    }

    @Test
    void version_texte_acceptee() {
        assertThat(load(minimalWith("config-version", String.valueOf(ConfigMigrator.LATEST_VERSION))).config()).isPresent();
    }

    @Test
    void une_configuration_en_version_1_est_migree_sans_transformation() {
        ConfigLoadResult result = load(minimalWith("config-version", 1));

        assertThat(result.config()).isPresent();
        assertThat(result.migrated()).isTrue();
        assertThat(result.tree()).isEqualTo(minimal());
    }

    @Test
    void une_configuration_en_version_2_perd_la_section_license_et_garde_les_valeurs_de_l_administrateur() {
        Map<String, Object> v2 = with(with(minimalWith("config-version", 2), "license",
                Map.of("key", "ANCIENNE-CLE", "grace-days", 5)), "limits.cooldown-ms", 900);

        ConfigLoadResult result = load(v2);

        assertThat(result.report().issues()).isEmpty();
        assertThat(result.migrated()).isTrue();
        assertThat(result.tree()).doesNotContainKey("license")
                .containsEntry("config-version", ConfigMigrator.LATEST_VERSION);
        assertThat(result.config().orElseThrow().limits().cooldownMs()).isEqualTo(900);
        assertThat(result.config().orElseThrow().items()).hasSize(1);
    }

    @Test
    void une_section_license_ajoutee_a_un_fichier_a_jour_est_signalee_comme_inconnue() {
        ConfigLoadResult result = load(minimalWith("license.key", "x"));

        assertThat(result.config()).isPresent();
        assertThat(warningPaths(result)).containsExactly("license");
    }

    @Test
    void arbre_du_resultat_non_modifiable_et_source_inchangee() {
        Map<String, Object> source = new java.util.LinkedHashMap<>(minimal());
        ConfigLoadResult result = load(source);
        assertThat(result.migrated()).isFalse();
        assertThat(result.tree()).isEqualTo(minimal());
        assertThatThrownBy(() -> result.tree().put("x", 1)).isInstanceOf(UnsupportedOperationException.class);
        assertThat(source).isEqualTo(minimal());
    }

    @Test
    void resultat_incoherent_refuse() {
        ValidationReport noError = ValidationReport.builder().build();
        assertThatThrownBy(() -> new ConfigLoadResult(Optional.empty(), noError, Map.of(), false))
                .isInstanceOf(IllegalArgumentException.class);
        NovaBankConfig config = TestConfigs.loadValid(minimal());
        assertThatThrownBy(() -> new ConfigLoadResult(Optional.of(config), ValidationReport.ofError("a", "b"), Map.of(), false))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
