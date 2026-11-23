package dev.novadev.novabank.bukkit.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class YamlSectionsTest {

    private static final String FILE = """
            config-version: 3
            language: fr

            # ------------
            # Banques
            # ------------
            banks:
              entity-types:
                - player
              # commentaire indente
              valuation-mode: DERIVED

            # ------------
            # Items
            # ------------
            items:
            - key: minecraft:stone
              price: "1"

            gui:
              rows: 6
            """;

    @Test
    void extrait_une_section_avec_ses_commentaires_indentes_sans_ceux_de_la_suivante() {
        assertThat(YamlSections.extract(FILE, "banks")).contains("""
                banks:
                  entity-types:
                    - player
                  # commentaire indente
                  valuation-mode: DERIVED""");
        assertThat(YamlSections.extract(FILE, "language")).contains("language: fr");
        assertThat(YamlSections.extract(FILE, "items")).contains("items:\n- key: minecraft:stone\n  price: \"1\"");
        assertThat(YamlSections.extract(FILE, "gui")).contains("gui:\n  rows: 6");
        assertThat(YamlSections.extract(FILE, "absente")).isEmpty();
    }

    @Test
    void remplace_une_section_sans_toucher_au_reste_ni_aux_commentaires_d_entete() {
        String edited = YamlSections.replace(FILE, "banks", "banks:\n  valuation-mode: ACCUMULATED");

        assertThat(edited).contains("# Banques\n# ------------\nbanks:\n  valuation-mode: ACCUMULATED\n\n# ---")
                .contains("config-version: 3\nlanguage: fr")
                .contains("items:\n- key: minecraft:stone")
                .doesNotContain("entity-types");
    }

    @Test
    void ajoute_une_section_absente_en_fin_de_fichier() {
        String edited = YamlSections.replace(FILE, "leaderboards", "leaderboards:\n  player:\n    size: 5");

        assertThat(edited).endsWith("gui:\n  rows: 6\n\nleaderboards:\n  player:\n    size: 5\n");
    }

    @Test
    void refuse_un_bloc_qui_glisserait_une_autre_section() {
        assertThat(YamlSections.wellFormed("gui", "gui:\n  rows: 3")).isTrue();
        assertThat(YamlSections.wellFormed("gui", "gui:\n  rows: 3\nstorage:\n  type: YAML")).isFalse();
        assertThat(YamlSections.wellFormed("gui", "items:\n  rows: 3")).isFalse();
        assertThat(YamlSections.wellFormed("items", "items:\n- key: a\n  price: \"1\"")).isTrue();
        assertThatThrownBy(() -> YamlSections.replace(FILE, "gui", "gui:\n  rows: 3\nredis:\n  enabled: true"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
