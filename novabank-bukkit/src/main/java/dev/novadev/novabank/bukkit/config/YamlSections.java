package dev.novadev.novabank.bukkit.config;

import dev.novadev.novabank.core.text.ConsoleText;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lecture et remplacement des sections de premier niveau d'un {@code config.yml}, EN TEXTE : les commentaires du reste
 * du fichier sont conserves tels quels. Une section va de sa ligne {@code cle:} jusqu'au bloc de commentaires de colonne 0
 * (et lignes vides) qui precede la section suivante ; les commentaires indentes lui appartiennent.
 */
public final class YamlSections {

    private static final Pattern KEY = Pattern.compile("^([A-Za-z0-9_-]+):(\\s.*)?$");

    private YamlSections() {
    }

    /**
     * @param yaml contenu du fichier
     * @param key  section de premier niveau
     * @return le texte de la section (sans retour a la ligne final), vide si elle est absente
     */
    public static Optional<String> extract(String yaml, String key) {
        List<String> lines = lines(yaml);
        int[] span = spans(lines).get(key);
        return span == null ? Optional.empty() : Optional.of(String.join("\n", lines.subList(span[0], span[1])));
    }

    /**
     * Remplace une section, ou l'ajoute en fin de fichier si elle est absente.
     *
     * @param yaml  contenu du fichier
     * @param key   section de premier niveau
     * @param block nouveau texte, qui doit passer {@link #wellFormed(String, String)}
     * @return le nouveau contenu
     * @throws IllegalArgumentException si le bloc est mal forme
     */
    public static String replace(String yaml, String key, String block) {
        if (!wellFormed(key, block)) {
            throw new IllegalArgumentException(ConsoleText.tr("Bloc invalide pour la section '{0}'", key));
        }
        List<String> lines = lines(yaml);
        int[] span = spans(lines).get(key);
        List<String> result = new ArrayList<>(lines);
        if (span == null) {
            while (!result.isEmpty() && result.get(result.size() - 1).isBlank()) {
                result.remove(result.size() - 1);
            }
            result.add("");
            result.addAll(List.of(block.split("\n", -1)));
            result.add("");
        } else {
            result.subList(span[0], span[1]).clear();
            result.addAll(span[0], List.of(block.split("\n", -1)));
        }
        return String.join("\n", result);
    }

    /**
     * Un bloc recu d'un autre serveur ne doit pas pouvoir glisser une autre section : sa premiere ligne est {@code cle:} et
     * chaque autre ligne est vide, indentee, un commentaire ou un element de liste.
     *
     * @param key   section attendue
     * @param block texte
     * @return {@code true} si le bloc ne contient que cette section
     */
    public static boolean wellFormed(String key, String block) {
        String[] lines = block.split("\n", -1);
        Matcher first = KEY.matcher(lines[0]);
        if (!first.matches() || !first.group(1).equals(key)) {
            return false;
        }
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (!(line.isBlank() || line.startsWith(" ") || line.startsWith("\t") || line.startsWith("#")
                    || line.startsWith("- "))) {
                return false;
            }
        }
        return true;
    }

    private static List<String> lines(String yaml) {
        return new ArrayList<>(List.of(yaml.split("\n", -1)));
    }

    /** @return pour chaque section, {debut inclus, fin exclue} en numeros de ligne */
    private static Map<String, int[]> spans(List<String> lines) {
        List<Integer> keyLines = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            Matcher matcher = KEY.matcher(lines.get(i));
            if (matcher.matches()) {
                keyLines.add(i);
                keys.add(matcher.group(1));
            }
        }
        Map<String, int[]> spans = new LinkedHashMap<>();
        for (int k = 0; k < keys.size(); k++) {
            int end = lines.size();
            if (k + 1 < keys.size()) {
                end = keyLines.get(k + 1);
                while (end - 1 > keyLines.get(k) && (lines.get(end - 1).isBlank() || lines.get(end - 1).startsWith("#"))) {
                    end--;
                }
            } else {
                while (end - 1 > keyLines.get(k) && lines.get(end - 1).isBlank()) {
                    end--;
                }
            }
            spans.put(keys.get(k), new int[] {keyLines.get(k), end});
        }
        return spans;
    }
}
