package dev.novadev.novabank.bukkit.config;

import dev.novadev.novabank.core.text.ConsoleText;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ecriture d'une valeur a un chemin de {@code config.yml} ({@code banks.roles.island.deposit}), EN TEXTE : tout le reste
 * du fichier (commentaires, ordre, mise en forme) est conserve. La valeur est ecrite sur la ligne de la cle, en YAML en
 * ligne ({@code 500}, {@code "texte"}, {@code [a, b]}, {@code {cle: v}}) ; si la cle portait un bloc (liste ou table sur
 * plusieurs lignes), ce bloc est remplace. Une cle absente est creee sous son plus proche parent existant, avec les
 * parents manquants (indentation de 2 espaces). Sans dependance serveur.
 */
public final class YamlPaths {

    private static final Pattern KEY_LINE = Pattern.compile("^( *)([A-Za-z0-9_.-]+):(?: (.*))?$");
    private static final int INDENT = 2;

    private YamlPaths() {
    }

    /**
     * @param yaml  texte du fichier
     * @param path  chemin de la cle, parties separees par des points (aucune partie ne contient de point)
     * @param value valeur YAML en ligne, deja mise en forme ({@link #quote}, {@link #inlineList}...)
     * @return le texte modifie
     * @throws IllegalArgumentException si le chemin est vide ou la valeur sur plusieurs lignes
     */
    public static String set(String yaml, String path, String value) {
        Objects.requireNonNull(yaml, "yaml");
        Objects.requireNonNull(value, "value");
        if (path == null || path.isBlank() || value.contains("\n")) {
            throw new IllegalArgumentException(ConsoleText.tr("Chemin vide ou valeur sur plusieurs lignes : {0}",
                    path));
        }
        List<String> lines = new ArrayList<>(Arrays.asList(yaml.split("\n", -1)));
        String[] parts = path.split("\\.");
        int parentLine = -1;
        int parentIndent = -INDENT;
        int depth = 0;
        while (depth < parts.length) {
            int found = findChild(lines, parentLine, parentIndent, parts[depth]);
            if (found < 0) {
                break;
            }
            parentLine = found;
            parentIndent = indentOf(lines.get(found));
            depth++;
        }
        if (depth == parts.length) {
            replaceValue(lines, parentLine, value);
        } else {
            insert(lines, parentLine, parentIndent, Arrays.copyOfRange(parts, depth, parts.length), value);
        }
        return String.join("\n", lines);
    }

    /**
     * @param text texte quelconque
     * @return une chaine YAML entre guillemets doubles (antislash et guillemet echappes)
     */
    public static String quote(String text) {
        return '"' + text.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    /**
     * @param items elements deja mis en forme
     * @return la liste en ligne {@code [a, b]}
     */
    public static String inlineList(List<String> items) {
        return "[" + String.join(", ", items) + "]";
    }

    /** Ligne de la cle {@code key} parmi les enfants directs de la ligne {@code parent} (-1 : racine), -1 si absente. */
    private static int findChild(List<String> lines, int parent, int parentIndent, String key) {
        int childIndent = -1;
        for (int i = parent + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (ignorable(line)) {
                continue;
            }
            int indent = indentOf(line);
            if (indent <= parentIndent) {
                return -1;
            }
            if (childIndent < 0) {
                childIndent = indent;
            }
            Matcher matcher = KEY_LINE.matcher(line);
            if (indent == childIndent && matcher.matches() && matcher.group(2).equals(key)) {
                return i;
            }
        }
        return -1;
    }

    private static void replaceValue(List<String> lines, int keyLine, String value) {
        String line = lines.get(keyLine);
        int indent = indentOf(line);
        Matcher matcher = KEY_LINE.matcher(line);
        String key = matcher.matches() ? matcher.group(2) : line.trim();
        lines.set(keyLine, " ".repeat(indent) + key + ": " + value);
        // Bloc de la cle (liste ou table sur plusieurs lignes) : remplace par la valeur en ligne.
        int end = blockEnd(lines, keyLine, indent);
        for (int i = end - 1; i > keyLine; i--) {
            lines.remove(i);
        }
    }

    /** Premiere ligne apres le bloc de la cle : premiere ligne significative d'indentation <= celle de la cle. */
    private static int blockEnd(List<String> lines, int keyLine, int indent) {
        int lastChild = keyLine;
        for (int i = keyLine + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (ignorable(line)) {
                continue;
            }
            boolean listItemAtSameIndent = indentOf(line) == indent && line.trim().startsWith("- ");
            if (indentOf(line) <= indent && !listItemAtSameIndent) {
                break;
            }
            lastChild = i;
        }
        return lastChild + 1;
    }

    private static void insert(List<String> lines, int parentLine, int parentIndent, String[] missing, String value) {
        int at = parentLine < 0 ? lastSignificant(lines) + 1 : blockEnd(lines, parentLine, parentIndent);
        if (parentLine >= 0) {
            // Un parent ecrit en ligne ({} ou []) devient une table pour recevoir ses enfants.
            String parent = lines.get(parentLine);
            Matcher matcher = KEY_LINE.matcher(parent);
            if (matcher.matches() && matcher.group(3) != null && !matcher.group(3).isBlank()) {
                lines.set(parentLine, " ".repeat(parentIndent) + matcher.group(2) + ":");
            }
        }
        List<String> added = new ArrayList<>();
        int indent = parentIndent + INDENT;
        for (int i = 0; i < missing.length; i++) {
            boolean leaf = i == missing.length - 1;
            added.add(" ".repeat(indent) + missing[i] + ":" + (leaf ? " " + value : ""));
            indent += INDENT;
        }
        lines.addAll(at, added);
    }

    private static int lastSignificant(List<String> lines) {
        for (int i = lines.size() - 1; i >= 0; i--) {
            if (!lines.get(i).isBlank()) {
                return i;
            }
        }
        return -1;
    }

    private static boolean ignorable(String line) {
        String trimmed = line.trim();
        return trimmed.isEmpty() || trimmed.startsWith("#");
    }

    private static int indentOf(String line) {
        int indent = 0;
        while (indent < line.length() && line.charAt(indent) == ' ') {
            indent++;
        }
        return indent;
    }
}
