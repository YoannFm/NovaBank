package dev.novadev.novabank.storage.file;

import dev.novadev.novabank.core.text.ConsoleText;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

/**
 * Instantane YAML par la SnakeYAML du serveur : 1.x sur les anciens serveurs (1.28 sur Paper
 * 1.17.1), 2.x sur les recents (2.2 sur Paper 1.21.11 et 26.3), deux API en partie incompatibles.
 * Seule la partie commune est utilisee :
 *
 * <ul>
 *   <li>ecriture : {@code new Yaml(DumperOptions)} et {@code dump} d'un arbre de {@link Map},
 *       {@link List} et {@link String} (les textes qui ressemblent a des nombres sont quotes par
 *       SnakeYAML, donc relus en texte ; un texte non imprimable est ecrit en {@code !!binary}) ;</li>
 *   <li>lecture : {@code compose}, qui produit l'arbre de noeuds SANS construire d'objet (aucune
 *       classe instanciee depuis le fichier, quelle que soit la version), converti ici en
 *       {@link Map} / {@link List} / {@link String}.</li>
 * </ul>
 *
 * <p>SnakeYAML 1.32+ et 2.x limitent par defaut un document a 3 Mo de caracteres : la limite est
 * relevee si la methode {@code LoaderOptions#setCodePointLimit} existe (seul appel par reflexion,
 * absent des versions qui n'ont pas cette limite).
 */
final class YamlSnapshotCodec implements SnapshotCodec {

    private static final int CODE_POINT_LIMIT = Integer.MAX_VALUE;
    private static final String BINARY_TAG = "tag:yaml.org,2002:binary";

    @Override
    public String extension() {
        return "yml";
    }

    @Override
    public void write(Map<String, Object> tree, Writer output) throws IOException {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndent(2);
        options.setWidth(Integer.MAX_VALUE);
        options.setAllowUnicode(true);
        try {
            new Yaml(options).dump(tree, output);
        } catch (YAMLException failure) {
            throw new IOException(ConsoleText.tr("YAML : ecriture impossible : {0}", failure.getMessage()), failure);
        }
    }

    @Override
    public Map<String, Object> read(Reader input) throws IOException {
        Node root;
        try {
            root = new Yaml(loaderOptions()).compose(input);
        } catch (YAMLException failure) {
            throw new IOException(ConsoleText.tr("YAML invalide : {0}", failure.getMessage()), failure);
        }
        if (!(root instanceof MappingNode)) {
            throw new IOException(ConsoleText.tr("YAML : table attendue a la racine"));
        }
        return mapping((MappingNode) root);
    }

    private static Object value(Node node) throws IOException {
        if (node instanceof MappingNode mapping) {
            return mapping(mapping);
        }
        if (node instanceof SequenceNode sequence) {
            List<Object> list = new ArrayList<>();
            for (Node element : sequence.getValue()) {
                list.add(value(element));
            }
            return list;
        }
        if (node instanceof ScalarNode scalar) {
            return text(scalar);
        }
        throw new IOException(ConsoleText.tr("YAML : noeud inattendu ligne {0}", (node.getStartMark().getLine() + 1)));
    }

    private static Map<String, Object> mapping(MappingNode node) throws IOException {
        Map<String, Object> map = new LinkedHashMap<>();
        for (NodeTuple tuple : node.getValue()) {
            if (!(tuple.getKeyNode() instanceof ScalarNode key)) {
                throw new IOException(ConsoleText.tr("YAML : cle non textuelle ligne {0}",
                        (tuple.getKeyNode().getStartMark().getLine() + 1)));
            }
            if (map.put(key.getValue(), value(tuple.getValueNode())) != null) {
                throw new IOException(ConsoleText.tr("YAML : cle en double '{0}'", key.getValue()));
            }
        }
        return map;
    }

    /**
     * SnakeYAML ecrit un texte contenant des caracteres non imprimables (controle, NUL) en
     * {@code !!binary} : base64 de ses octets UTF-8, decode ici.
     */
    private static String text(ScalarNode scalar) throws IOException {
        if (!BINARY_TAG.equals(scalar.getTag().getValue())) {
            return scalar.getValue();
        }
        try {
            return new String(Base64.getMimeDecoder().decode(scalar.getValue()), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException invalid) {
            throw new IOException(ConsoleText.tr("YAML : valeur binaire invalide ligne {0}",
                    (scalar.getStartMark().getLine() + 1)),
                    invalid);
        }
    }

    private static LoaderOptions loaderOptions() throws IOException {
        LoaderOptions options = new LoaderOptions();
        Method limit;
        try {
            limit = LoaderOptions.class.getMethod("setCodePointLimit", int.class);
        } catch (NoSuchMethodException absent) {
            return options;
        }
        try {
            limit.invoke(options, CODE_POINT_LIMIT);
        } catch (IllegalAccessException | InvocationTargetException failure) {
            throw new IOException(ConsoleText.tr("YAML : limite de taille de SnakeYAML non relevable : {0}",
                    failure), failure);
        }
        return options;
    }
}
