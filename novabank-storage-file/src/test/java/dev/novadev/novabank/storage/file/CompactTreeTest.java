package dev.novadev.novabank.storage.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Forme compacte des lignes du journal.
 */
class CompactTreeTest {

    @Test
    void un_arbre_piege_tient_sur_une_ligne_et_est_relu_a_l_identique() {
        Map<String, Object> tree = CodecFixtures.trickyTree();

        String line = CompactTree.write(tree);

        assertThat(line).doesNotContain("\n").doesNotContain("\r").doesNotContain("\u2028");
        assertThat(CompactTree.read(line)).isEqualTo(tree);
    }

    @Test
    void une_forme_incomplete_ou_invalide_est_refusee() {
        for (String invalid : new String[] {"", "{", "{\"a\":\"b\"", "{\"a\":1}", "{\"a\":\"b\"}x", "[\"a\"]",
                "{\"a\":\"\\x\"}", "{\"a\":\"b\",\"a\":\"c\"}", "{\"a\":\"\\u12\"}"}) {
            assertThatThrownBy(() -> CompactTree.read(invalid)).as(invalid).isInstanceOf(CorruptDataException.class);
        }
    }
}
