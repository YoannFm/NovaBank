package dev.novadev.novabank.config.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.novadev.novabank.config.ConfigLoadResult;
import dev.novadev.novabank.config.FakeItemAvailability;
import dev.novadev.novabank.config.NovaBankConfigLoader;
import dev.novadev.novabank.config.TestConfigs;
import dev.novadev.novabank.config.validation.ValidationIssue;
import dev.novadev.novabank.config.validation.ValidationReport;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ConfigMigratorTest {

    private final List<Integer> applied = new ArrayList<>();

    /** v1 -> v2 : server.name renomme en server.id. */
    private final ConfigMigration renameServerName = new ConfigMigration() {
        @Override
        public int fromVersion() {
            return 1;
        }

        @Override
        public Map<String, Object> migrate(Map<String, Object> tree) {
            applied.add(1);
            @SuppressWarnings("unchecked")
            Map<String, Object> server = (Map<String, Object>) tree.get("server");
            if (server != null && server.containsKey("name")) {
                server.put("id", server.remove("name"));
            }
            return tree;
        }
    };

    /** v2 -> v3 : ajoute diagnostics.debug. */
    private final ConfigMigration addDebug = new ConfigMigration() {
        @Override
        public int fromVersion() {
            return 2;
        }

        @Override
        public Map<String, Object> migrate(Map<String, Object> tree) {
            applied.add(2);
            tree.put("diagnostics", Map.of("debug", true));
            return tree;
        }
    };

    private ConfigMigrator chain() {
        return new ConfigMigrator(3, List.of(addDebug, renameServerName));
    }

    @Test
    void chaine_appliquee_dans_l_ordre_depuis_la_version_lue() {
        ValidationReport.Builder report = ValidationReport.builder();
        Map<String, Object> source = Map.of("config-version", 1, "server", Map.of("name", "lobby"));
        ConfigMigrator.Migration migration = chain().migrate(source, report).orElseThrow();
        assertThat(applied).containsExactly(1, 2);
        assertThat(migration.sourceVersion()).isEqualTo(1);
        assertThat(migration.tree()).containsEntry("config-version", 3)
                .containsEntry("server", Map.of("id", "lobby"))
                .containsEntry("diagnostics", Map.of("debug", true));
        assertThat(source).containsEntry("server", Map.of("name", "lobby"));
        assertThat(report.build().issues()).isEmpty();
    }

    @Test
    void seules_les_etapes_manquantes_sont_appliquees() {
        Optional<ConfigMigrator.Migration> migration = chain().migrate(Map.of("config-version", 2),
                ValidationReport.builder());
        assertThat(applied).containsExactly(2);
        assertThat(migration.orElseThrow().sourceVersion()).isEqualTo(2);
        applied.clear();
        chain().migrate(Map.of("config-version", 3), ValidationReport.builder());
        assertThat(applied).isEmpty();
    }

    @Test
    void version_absente_supposee_courante_avec_avertissement() {
        ValidationReport.Builder report = ValidationReport.builder();
        assertThat(chain().migrate(Map.of(), report).orElseThrow().sourceVersion()).isEqualTo(3);
        assertThat(report.build().warnings()).extracting(ValidationIssue::path).containsExactly("config-version");
        assertThat(applied).isEmpty();
    }

    @Test
    void version_trop_recente_ou_invalide_refusee() {
        for (Object version : List.of(4, 0, "x", 1.5)) {
            ValidationReport.Builder report = ValidationReport.builder();
            assertThat(chain().migrate(Map.of("config-version", version), report)).isEmpty();
            assertThat(report.build().errors()).extracting(ValidationIssue::path).containsExactly("config-version");
        }
    }

    @Test
    void etape_en_echec_signalee_sans_exception() {
        ConfigMigration failing = new ConfigMigration() {
            @Override
            public int fromVersion() {
                return 1;
            }

            @Override
            public Map<String, Object> migrate(Map<String, Object> tree) {
                throw new IllegalStateException("arbre inattendu");
            }
        };
        ValidationReport.Builder report = ValidationReport.builder();
        assertThat(new ConfigMigrator(2, List.of(failing)).migrate(Map.of("config-version", 1), report)).isEmpty();
        assertThat(report.build().errors()).singleElement()
                .satisfies(issue -> assertThat(issue.message()).contains("version 1 vers 2").contains("arbre inattendu"));
    }

    @Test
    void chaine_incoherente_refusee_a_la_construction() {
        assertThatThrownBy(() -> new ConfigMigrator(0, List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConfigMigrator(3, List.of(renameServerName)))
                .hasMessageContaining("manquante depuis la version 2");
        assertThatThrownBy(() -> new ConfigMigrator(2, List.of(renameServerName, renameServerName)))
                .hasMessageContaining("Deux migrations");
        assertThatThrownBy(() -> new ConfigMigrator(2, List.of(addDebug))).hasMessageContaining("hors bornes");
    }

    @Test
    void migrateur_standard_version_1_vers_3_sans_autre_transformation() {
        ValidationReport.Builder report = ValidationReport.builder();
        Map<String, Object> v1 = Map.of("config-version", 1, "server", Map.of("id", "lobby"));

        ConfigMigrator.Migration migration = ConfigMigrator.standard().migrate(v1, report).orElseThrow();

        assertThat(ConfigMigrator.standard().targetVersion()).isEqualTo(ConfigMigrator.LATEST_VERSION).isEqualTo(7);
        assertThat(migration.tree()).isEqualTo(Map.of("config-version", 7, "server", Map.of("id", "lobby")));
        assertThat(migration.sourceVersion()).isEqualTo(1);
        assertThat(report.build().issues()).isEmpty();
    }

    @Test
    void migrateur_standard_version_2_vers_3_retire_la_section_license_et_garde_le_reste() {
        ValidationReport.Builder report = ValidationReport.builder();
        Map<String, Object> v2 = Map.of("config-version", 2, "license", Map.of("key", "ANCIENNE-CLE", "grace-days", 5),
                "limits", Map.of("cooldown-ms", 900));

        ConfigMigrator.Migration migration = ConfigMigrator.standard().migrate(v2, report).orElseThrow();

        assertThat(migration.tree()).isEqualTo(Map.of("config-version", 7, "limits", Map.of("cooldown-ms", 900)));
        assertThat(migration.sourceVersion()).isEqualTo(2);
        assertThat(report.build().issues()).isEmpty();
    }

    @Test
    void cles_retirees_depuis_une_version_pour_la_mise_a_jour_du_fichier() {
        ConfigMigrator standard = ConfigMigrator.standard();
        assertThat(standard.removedKeysSince(0)).containsExactly("license");
        assertThat(standard.removedKeysSince(1)).containsExactly("license");
        assertThat(standard.removedKeysSince(2)).containsExactly("license");
        assertThat(standard.removedKeysSince(3)).isEmpty();
        assertThat(chain().removedKeysSince(1)).isEmpty();
    }

    @Test
    void retrait_d_une_cle_imbriquee_sans_creer_de_section_ni_toucher_a_une_valeur_simple() {
        RemovedKeysMigration migration = new RemovedKeysMigration(1, Set.of("a.b", "x.y", "v.w"));
        Map<String, Object> tree = new HashMap<>(Map.of("a", new HashMap<>(Map.of("b", 1, "c", 2)), "v", "simple"));

        assertThat(migration.migrate(tree)).isEqualTo(Map.of("a", Map.of("c", 2), "v", "simple"));
        assertThat(migration.removedKeys()).containsExactlyInAnyOrder("a.b", "x.y", "v.w");
    }

    @Test
    void chargeur_applique_la_migration_avant_validation() {
        NovaBankConfigLoader loader = new NovaBankConfigLoader(FakeItemAvailability.everything(),
                new ConfigMigrator(2, List.of(renameServerName)));
        Map<String, Object> v1 = TestConfigs.with(TestConfigs.with(TestConfigs.minimal(), "server.name", "lobby"),
                "config-version", 1);
        ConfigLoadResult result = loader.load(v1);
        assertThat(result.report().issues()).isEmpty();
        assertThat(result.migrated()).isTrue();
        assertThat(result.config().orElseThrow().server().id()).isEqualTo("lobby");
        assertThat(result.tree()).containsEntry("config-version", 2);
    }
}
