package dev.novadev.novabank.bukkit.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Remplace la valeur d'une cle SCALAIRE d'un fichier YAML en conservant tout le reste (commentaires, ordre,
 * mise en forme), ce que la serialisation de Bukkit ne permet pas avant 1.18.1. Ne gere que les cles deja
 * presentes avec une valeur sur la meme ligne : sections, listes et cles absentes sont refusees (vide).
 */
public final class YamlScalarEditor {

    private static final Pattern KEY_LINE = Pattern.compile("^( *)([A-Za-z0-9_-]+):(?:[ \\t]+(.*))?$");
    private static final Pattern PLAIN = Pattern.compile("[A-Za-z0-9_.+-]+");

    private YamlScalarEditor() {
    }

    /**
     * @param yaml     contenu du fichier
     * @param path     chemin pointe, par exemple {@code limits.cooldown-ms}
     * @param newValue nouvelle valeur (texte saisi)
     * @return le nouveau contenu, ou vide si la cle est absente, n'est pas un scalaire ou si la valeur est inexprimable
     */
    public static Optional<String> replace(String yaml, String path, String newValue) {
        if (newValue.indexOf('\n') >= 0 || newValue.indexOf('\r') >= 0) {
            return Optional.empty();
        }
        String[] wanted = path.split("\\.");
        List<String> lines = new ArrayList<>(List.of(yaml.split("\n", -1)));
        List<Integer> indents = new ArrayList<>();
        List<String> stack = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            Matcher match = KEY_LINE.matcher(line);
            if (line.isBlank() || line.stripLeading().startsWith("#") || line.stripLeading().startsWith("-")
                    || !match.matches()) {
                continue;
            }
            int indent = match.group(1).length();
            while (!indents.isEmpty() && indents.get(indents.size() - 1) >= indent) {
                indents.remove(indents.size() - 1);
                stack.remove(stack.size() - 1);
            }
            indents.add(indent);
            stack.add(match.group(2));
            if (stack.size() != wanted.length || !stack.equals(List.of(wanted))) {
                continue;
            }
            String rest = match.group(3);
            if (rest == null || rest.isBlank() || rest.startsWith("#") || rest.startsWith("[") || rest.startsWith("{")
                    || rest.startsWith("|") || rest.startsWith(">")) {
                return Optional.empty();
            }
            String comment = trailingComment(rest);
            lines.set(i, match.group(1) + match.group(2) + ": " + format(newValue) + comment);
            return Optional.of(String.join("\n", lines));
        }
        return Optional.empty();
    }

    private static String format(String value) {
        if (PLAIN.matcher(value).matches()) {
            return value;
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /** Commentaire de fin de ligne ({@code " # ..."}) hors guillemets, ou chaine vide. */
    private static String trailingComment(String rest) {
        boolean single = false;
        boolean dbl = false;
        for (int i = 0; i < rest.length(); i++) {
            char c = rest.charAt(i);
            if (c == '\'' && !dbl) {
                single = !single;
            } else if (c == '"' && !single) {
                dbl = !dbl;
            } else if (c == '#' && !single && !dbl && i > 0 && Character.isWhitespace(rest.charAt(i - 1))) {
                return "  " + rest.substring(i);
            }
        }
        return "";
    }
}
