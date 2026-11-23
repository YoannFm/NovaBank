package dev.novadev.novabank.bukkit.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class YamlPathsTest {

    private static final String YAML = String.join("\n",
            "# En-tete",
            "language: fr",
            "",
            "# Banques",
            "banks:",
            "  # Types actifs",
            "  entity-types:",
            "    - player",
            "  default-priority:",
            "    - player",
            "  roles:",
            "    player:",
            "      deposit: [owner, admin, member]",
            "      withdraw: [owner, admin]",
            "",
            "# Stockage",
            "storage:",
            "  sql:",
            "    password: \"\"",
            "    options: {}",
            "limits:",
            "  cooldown-ms: 500",
            "");

    private static Map<String, Object> parse(String yaml) throws Exception {
        YamlConfiguration parsed = new YamlConfiguration();
        parsed.loadFromString(yaml);
        return YamlTrees.toMap(parsed);
    }

    @Test
    void remplace_une_valeur_simple_sans_toucher_au_reste() {
        String edited = YamlPaths.set(YAML, "limits.cooldown-ms", "1000");

        assertThat(edited).contains("  cooldown-ms: 1000").contains("# Types actifs").contains("# Stockage")
                .doesNotContain("cooldown-ms: 500");
        assertThat(edited.lines().count()).isEqualTo(YAML.lines().count());
    }

    @Test
    void remplace_une_liste_en_bloc_par_une_liste_en_ligne() throws Exception {
        String edited = YamlPaths.set(YAML, "banks.entity-types", YamlPaths.inlineList(List.of("player", "island")));

        assertThat(edited).contains("  entity-types: [player, island]\n  default-priority:").contains("# Types actifs");
        assertThat(parse(edited)).extractingByKey("banks").asInstanceOf(
                org.assertj.core.api.InstanceOfAssertFactories.MAP).containsEntry("entity-types", List.of("player", "island"))
                .containsEntry("default-priority", List.of("player"));
    }

    @Test
    void cree_une_cle_absente_et_ses_parents() throws Exception {
        String edited = YamlPaths.set(YAML, "banks.roles.island.withdraw", YamlPaths.inlineList(List.of("owner")));
        edited = YamlPaths.set(edited, "leaderboards.island.size", "20");

        Map<String, Object> tree = parse(edited);
        assertThat(tree.get("banks")).asString().contains("island={withdraw=[owner]}").contains("player={deposit");
        assertThat(tree.get("leaderboards")).asString().isEqualTo("{island={size=20}}");
        assertThat(edited).contains("    island:\n      withdraw: [owner]\n\n# Stockage");
    }

    @Test
    void un_parent_en_ligne_devient_une_table() throws Exception {
        String edited = YamlPaths.set(YAML, "storage.sql.options.sslMode", YamlPaths.quote("verify-full"));

        assertThat(parse(edited).get("storage")).asString().contains("options={sslMode=verify-full}");
    }

    @Test
    void les_chaines_sont_echappees() throws Exception {
        String edited = YamlPaths.set(YAML, "storage.sql.password", YamlPaths.quote("a\"b\\c d#e"));

        assertThat(parse(edited).get("storage")).asString().contains("password=a\"b\\c d#e");
    }

    @Test
    void chemin_vide_ou_valeur_multiligne_refuses() {
        assertThatThrownBy(() -> YamlPaths.set(YAML, "", "1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> YamlPaths.set(YAML, "language", "a\nb")).isInstanceOf(IllegalArgumentException.class);
    }
}
