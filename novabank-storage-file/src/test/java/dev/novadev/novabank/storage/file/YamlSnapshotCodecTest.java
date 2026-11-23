package dev.novadev.novabank.storage.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Format YAML, execute avec SnakeYAML 1.28 (Spigot/Paper 1.17.1) puis 2.x (serveurs recents).
 */
class YamlSnapshotCodecTest {

    private final SnapshotCodec codec = new YamlSnapshotCodec();

    @Test
    void la_version_de_snakeyaml_chargee_est_celle_attendue_par_cette_execution() {
        CodecFixtures.assertLibraryVersion(Yaml.class, "snakeyaml", "novabank.test.expected-snakeyaml");
    }

    @Test
    void les_textes_pieges_sont_relus_a_l_identique() throws IOException {
        Map<String, Object> tree = CodecFixtures.trickyTree();

        assertThat(CodecFixtures.roundTrip(codec, tree)).isEqualTo(tree);
    }

    @Test
    void le_fichier_est_en_blocs_lisibles_et_les_nombres_sont_des_textes_quotes() throws IOException {
        String yaml = CodecFixtures.written(codec, CodecFixtures.trickyTree());

        assertThat(yaml).contains("format: novabank-file\n").contains("number: '123'").contains("boolean: 'true'");
        assertThat(yaml).doesNotContain("{a:");
    }

    @Test
    void un_texte_non_imprimable_ecrit_en_binaire_est_relu_a_l_identique() throws IOException {
        Map<String, Object> tree = new LinkedHashMap<>(Map.of("id", "faction\u0000\u001bnom \u00e9"));

        assertThat(CodecFixtures.written(codec, tree)).contains("!!binary");
        assertThat(CodecFixtures.roundTrip(codec, tree)).isEqualTo(tree);
    }

    @Test
    void un_nombre_saisi_a_la_main_sans_guillemets_est_relu_en_texte_exact() throws IOException {
        Map<String, Object> tree = codec.read(new StringReader("version: 12345678901234567890\nvalue: 0.1000\n"));

        assertThat(tree).containsEntry("version", "12345678901234567890").containsEntry("value", "0.1000");
    }

    @Test
    void une_etiquette_de_classe_java_n_instancie_rien() {
        // SnakeYAML 1.x : etiquette ignoree par compose (texte) ; 2.x : refusee. Jamais d'objet cree.
        try {
            Map<String, Object> tree = codec.read(new StringReader("danger: !!java.io.File /etc/passwd\n"));
            assertThat(tree).containsEntry("danger", "/etc/passwd");
        } catch (IOException refused) {
            assertThat(refused).hasMessageContaining("Global tag is not allowed");
        }
    }

    @Test
    void un_instantane_de_plus_de_3_mo_est_relu() throws IOException {
        Map<String, Object> tree = new LinkedHashMap<>();
        List<Object> lines = new ArrayList<>();
        for (int index = 0; index < 70_000; index++) {
            lines.add(new LinkedHashMap<>(Map.of("item", "minecraft:stone_" + index, "value", "1234.5678")));
        }
        tree.put("lines", lines);
        String yaml = CodecFixtures.written(codec, tree);
        assertThat(yaml.length()).isGreaterThan(3 * 1024 * 1024);

        assertThat(codec.read(new StringReader(yaml))).isEqualTo(tree);
    }

    @Test
    void une_syntaxe_invalide_ou_une_cle_en_double_est_refusee() {
        assertThatThrownBy(() -> codec.read(new StringReader("a: [b\n"))).isInstanceOf(IOException.class)
                .hasMessageContaining("YAML invalide");
        assertThatThrownBy(() -> codec.read(new StringReader("a: 1\na: 2\n"))).isInstanceOf(IOException.class)
                .hasMessageContaining("double");
        assertThatThrownBy(() -> codec.read(new StringReader("- a\n"))).isInstanceOf(IOException.class)
                .hasMessageContaining("racine");
    }
}
