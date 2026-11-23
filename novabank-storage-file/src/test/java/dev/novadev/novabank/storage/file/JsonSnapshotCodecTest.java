package dev.novadev.novabank.storage.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.stream.JsonReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Format JSON, execute avec Gson 2.8.0 (Spigot/Paper 1.17.1) puis une version recente.
 */
class JsonSnapshotCodecTest {

    private final SnapshotCodec codec = new JsonSnapshotCodec();

    @Test
    void la_version_de_gson_chargee_est_celle_attendue_par_cette_execution() {
        CodecFixtures.assertLibraryVersion(JsonReader.class, "gson", "novabank.test.expected-gson");
    }

    @Test
    void les_textes_pieges_sont_relus_a_l_identique() throws IOException {
        Map<String, Object> tree = CodecFixtures.trickyTree();

        assertThat(CodecFixtures.roundTrip(codec, tree)).isEqualTo(tree);
    }

    @Test
    void le_fichier_est_indente_et_les_nombres_sont_des_textes() throws IOException {
        String json = CodecFixtures.written(codec, CodecFixtures.trickyTree());

        assertThat(json).startsWith("{\n  \"format\": \"novabank-file\",").contains("\"number\": \"123\"");
    }

    @Test
    void un_nombre_saisi_a_la_main_est_relu_en_texte_exact() throws IOException {
        Map<String, Object> tree = codec.read(new StringReader("{\"version\": 12345678901234567890, \"v\": 0.1000}"));

        assertThat(tree).containsEntry("version", "12345678901234567890").containsEntry("v", "0.1000");
    }

    @Test
    void une_syntaxe_invalide_ou_une_cle_en_double_est_refusee() {
        assertThatThrownBy(() -> codec.read(new StringReader("{\"a\": [\"b\"}"))).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> codec.read(new StringReader("{\"a\": \"1\", \"a\": \"2\"}")))
                .isInstanceOf(IOException.class).hasMessageContaining("double");
        assertThatThrownBy(() -> codec.read(new StringReader("[\"a\"]"))).isInstanceOf(IOException.class)
                .hasMessageContaining("racine");
        assertThatThrownBy(() -> codec.read(new StringReader("{\"a\": true}"))).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> codec.read(new StringReader("{\"a\": \"1\"} {}"))).isInstanceOf(IOException.class);
    }
}
