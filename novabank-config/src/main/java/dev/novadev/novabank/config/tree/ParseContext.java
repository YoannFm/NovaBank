package dev.novadev.novabank.config.tree;

import dev.novadev.novabank.config.validation.ValidationReport;
import dev.novadev.novabank.core.text.ConsoleText;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Etat partage par tous les {@link ConfigNode} d'une meme lecture : issues accumulees
 * et chemins lus par les parseurs.
 *
 * <p>Un chemin est <em>reconnu</em> s'il a ete lu (meme absent, la valeur par defaut
 * s'appliquant) ou s'il se trouve sous une section libre (options JDBC). Les cles non reconnues sont signalees par
 * {@link #reportUnreadKeys()} : faute de frappe probable.
 */
public final class ParseContext {

    private final ValidationReport.Builder report = ValidationReport.builder();
    private final Set<String> readPaths = new HashSet<>();
    private final Set<String> freePaths = new HashSet<>();
    private final List<ConfigNode> nodes = new ArrayList<>();

    /**
     * Accumulateur des issues de cette lecture.
     *
     * @return le constructeur de rapport
     */
    public ValidationReport.Builder report() {
        return report;
    }

    /**
     * Indique si un chemin est connu des parseurs pour l'arbre lu.
     *
     * @param path chemin teste
     * @return {@code true} s'il a ete lu ou se trouve sous une section libre
     */
    public boolean isRecognized(String path) {
        if (readPaths.contains(path)) {
            return true;
        }
        String current = path;
        while (true) {
            if (freePaths.contains(current)) {
                return true;
            }
            int separator = current.lastIndexOf(ConfigTrees.SEPARATOR);
            if (separator < 0) {
                return false;
            }
            current = current.substring(0, separator);
        }
    }

    /**
     * Ajoute un avertissement pour chaque cle presente dans l'arbre mais jamais lue.
     * A appeler une fois, apres tous les parseurs.
     */
    public void reportUnreadKeys() {
        nodes.forEach(node -> node.keys().stream()
                .map(node::pathOf)
                .filter(path -> !isRecognized(path))
                .forEach(path -> report.warning(path, ConsoleText.tr("cle inconnue, ignoree (faute de frappe ?)"))));
    }

    void register(ConfigNode node) {
        nodes.add(node);
    }

    void markRead(String path) {
        readPaths.add(path);
    }

    void markFree(String path) {
        freePaths.add(path);
    }
}
