package dev.novadev.novabank.storage.file;

import dev.novadev.novabank.core.text.ConsoleText;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Lecture typee d'un noeud d'arbre ({@link Map} de textes, listes et tables), avec le chemin du
 * champ dans les messages d'erreur.
 */
final class TreeReader {

    private final Map<?, ?> node;
    private final String path;

    TreeReader(Map<?, ?> node, String path) {
        this.node = Objects.requireNonNull(node, "node");
        this.path = path;
    }

    /**
     * @param value racine lue
     * @param path  nom de la racine (fichier)
     * @return le lecteur de la racine
     * @throws CorruptDataException si la racine n'est pas une table
     */
    static TreeReader root(Object value, String path) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new CorruptDataException(ConsoleText.tr("{0} : table attendue", path));
        }
        return new TreeReader(map, path);
    }

    boolean has(String key) {
        return node.containsKey(key);
    }

    String text(String key) {
        Object value = node.get(key);
        if (!(value instanceof String text)) {
            throw new CorruptDataException(ConsoleText.tr("{0} : texte attendu{1}",
                    where(key), (value == null ? " (absent)" : "")));
        }
        return text;
    }

    String optionalText(String key) {
        return has(key) ? text(key) : null;
    }

    long longValue(String key) {
        String text = text(key);
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException invalid) {
            throw new CorruptDataException(ConsoleText.tr("{0} : entier attendu, lu '{1}'", where(key), text));
        }
    }

    Instant instant(String key) {
        String text = text(key);
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException invalid) {
            throw new CorruptDataException(ConsoleText.tr("{0} : date ISO-8601 attendue, lu '{1}'", where(key), text));
        }
    }

    TreeReader child(String key) {
        Object value = node.get(key);
        if (!(value instanceof Map<?, ?> map)) {
            throw new CorruptDataException(ConsoleText.tr("{0} : table attendue", where(key)));
        }
        return new TreeReader(map, where(key));
    }

    /**
     * @param key cle d'une liste de tables, absente = liste vide
     * @return les lecteurs des elements
     */
    List<TreeReader> children(String key) {
        Object value = node.get(key);
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw new CorruptDataException(ConsoleText.tr("{0} : liste attendue", where(key)));
        }
        List<TreeReader> children = new ArrayList<>(list.size());
        for (int index = 0; index < list.size(); index++) {
            Object element = list.get(index);
            String elementPath = where(key) + "[" + index + "]";
            if (!(element instanceof Map<?, ?> map)) {
                throw new CorruptDataException(ConsoleText.tr("{0} : table attendue", elementPath));
            }
            children.add(new TreeReader(map, elementPath));
        }
        return children;
    }

    String path() {
        return path;
    }

    private String where(String key) {
        return path + "." + key;
    }
}
