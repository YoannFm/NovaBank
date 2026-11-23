package dev.novadev.novabank.storage.file;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Arbres de test des formats d'instantane et verification de la version de bibliotheque
 * reellement chargee (1.28 / 2.8.0 dans l'execution par defaut, versions recentes dans la seconde).
 */
final class CodecFixtures {

    private CodecFixtures() {
    }

    /**
     * @return un arbre dont les textes piegent les formats : nombres, booleens, nuls YAML,
     *         guillemets, sauts de ligne, unicode, texte vide
     */
    static Map<String, Object> trickyTree() {
        Map<String, Object> tree = new LinkedHashMap<>();
        tree.put("format", "novabank-file");
        tree.put("number", "123");
        tree.put("negative", "-0012.3400");
        tree.put("big", "99999999999999999999999999999999.1234");
        tree.put("long", Long.toString(Long.MAX_VALUE));
        tree.put("boolean", "true");
        tree.put("yaml-no", "no");
        tree.put("null-word", "null");
        tree.put("tilde", "~");
        tree.put("empty", "");
        tree.put("quotes", "Les Batisseurs \"du\" Nord, l'ile");
        tree.put("special", "a: b # c - [d] {e} & * ! | > % @ `");
        tree.put("lines", "ligne 1\nligne 2\r\n\ttabulee");
        tree.put("unicode", "\u00e9\u00e8 \u2603 \ud83d\ude00 \u0000 \u001b \u2028");
        tree.put("spaces", "  devant et derriere  ");
        List<Object> list = new ArrayList<>();
        list.add("1");
        list.add(new LinkedHashMap<>(Map.of("item", "nexo:ruby_block")));
        list.add(new ArrayList<>());
        tree.put("list", list);
        tree.put("nested", new LinkedHashMap<>(Map.of("a", new LinkedHashMap<>(Map.of("b", "c")))));
        return tree;
    }

    static Map<String, Object> roundTrip(SnapshotCodec codec, Map<String, Object> tree) throws IOException {
        StringWriter written = new StringWriter();
        codec.write(tree, written);
        return codec.read(new StringReader(written.toString()));
    }

    static String written(SnapshotCodec codec, Map<String, Object> tree) throws IOException {
        StringWriter written = new StringWriter();
        codec.write(tree, written);
        return written.toString();
    }

    /**
     * @param type     classe de la bibliotheque
     * @param artifact nom de l'artefact ({@code snakeyaml}, {@code gson})
     * @param property propriete systeme donnant la version attendue
     */
    static void assertLibraryVersion(Class<?> type, String artifact, String property) {
        String expected = System.getProperty(property);
        assertThat(expected).as("propriete " + property + " fournie par surefire").isNotBlank();
        assertThat(type.getProtectionDomain().getCodeSource().getLocation().toString())
                .contains(artifact + "-" + expected + ".jar");
    }
}
