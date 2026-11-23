package dev.novadev.novabank.config.tree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Operations sur l'arbre generique de configuration et construction des chemins.
 *
 * <p>Un arbre est une {@code Map} dont les valeurs sont des {@code Map}, des {@code List}
 * ou des scalaires. Les copies produites ici ont des cles {@code String}, conservent
 * l'ordre du fichier et sont non modifiables.
 */
public final class ConfigTrees {

    /** Separateur des niveaux d'un chemin, par exemple {@code storage.sql.port}. */
    public static final char SEPARATOR = '.';

    private ConfigTrees() {
    }

    /**
     * Copie profonde non modifiable d'un arbre. Les cles sont converties en texte.
     *
     * @param tree arbre source
     * @return la copie
     */
    public static Map<String, Object> immutableCopy(Map<?, ?> tree) {
        return copyMap(tree, true);
    }

    /**
     * Copie profonde modifiable d'un arbre (sections et listes comprises). Les cles
     * sont converties en texte.
     *
     * @param tree arbre source
     * @return la copie
     */
    public static Map<String, Object> mutableCopy(Map<?, ?> tree) {
        return copyMap(tree, false);
    }

    private static Map<String, Object> copyMap(Map<?, ?> source, boolean immutable) {
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(String.valueOf(key), copyValue(value, immutable)));
        return immutable ? Collections.unmodifiableMap(copy) : copy;
    }

    private static Object copyValue(Object value, boolean immutable) {
        if (value instanceof Map<?, ?> map) {
            return copyMap(map, immutable);
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            list.forEach(element -> copy.add(copyValue(element, immutable)));
            return immutable ? Collections.unmodifiableList(copy) : copy;
        }
        return value;
    }

    /**
     * Copie de l'arbre ou la cle designee par {@code path} recoit {@code value}. Les
     * sections intermediaires absentes sont creees ; une valeur {@code null} retire la cle
     * (retour a la valeur par defaut).
     *
     * @param tree  arbre source, inchange
     * @param path  chemin pointe, par exemple {@code limits.cooldown-ms}
     * @param value nouvelle valeur, ou {@code null} pour retirer la cle
     * @return le nouvel arbre, ou vide si un niveau intermediaire existe sans etre une section
     */
    public static Optional<Map<String, Object>> withValue(Map<?, ?> tree, String path, Object value) {
        String[] segments = path.split("\\" + SEPARATOR, -1);
        return withValue(immutableCopy(tree), segments, 0, value);
    }

    private static Optional<Map<String, Object>> withValue(Object node, String[] segments, int index, Object value) {
        if (node != null && !(node instanceof Map)) {
            return Optional.empty();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        if (node instanceof Map<?, ?> map) {
            map.forEach((key, child) -> copy.put(String.valueOf(key), child));
        }
        String key = segments[index];
        if (index == segments.length - 1) {
            if (value == null) {
                copy.remove(key);
            } else {
                copy.put(key, copyValue(value, true));
            }
            return Optional.of(Collections.unmodifiableMap(copy));
        }
        return withValue(copy.get(key), segments, index + 1, value).map(child -> {
            copy.put(key, child);
            return Collections.unmodifiableMap(copy);
        });
    }

    /**
     * Chemin d'une cle sous un parent.
     *
     * @param parent chemin du parent, vide pour la racine
     * @param key    cle
     * @return {@code parent.key}, ou {@code key} a la racine
     */
    public static String child(String parent, String key) {
        return parent.isEmpty() ? key : parent + SEPARATOR + key;
    }

    /**
     * Chemin d'un element de liste.
     *
     * @param list  chemin (ou cle) de la liste
     * @param index position de l'element, a partir de 0
     * @return {@code list[index]}
     */
    public static String element(String list, int index) {
        return list + '[' + index + ']';
    }
}
