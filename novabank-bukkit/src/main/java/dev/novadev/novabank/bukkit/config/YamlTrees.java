package dev.novadev.novabank.bukkit.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Convertit une {@link ConfigurationSection} Bukkit en arbre generique : sections
 * imbriquees en {@link Map} (ordre conserve), listes conservees (y compris les listes de
 * maps, que Bukkit laisse en {@code Map}), scalaires inchanges.
 */
public final class YamlTrees {

    private YamlTrees() {
    }

    /**
     * @param section section lue (racine du fichier en general)
     * @return arbre non modifiable, cles en texte
     */
    public static Map<String, Object> toMap(ConfigurationSection section) {
        Objects.requireNonNull(section, "section");
        Map<String, Object> tree = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            tree.put(key, convert(section.get(key)));
        }
        return Collections.unmodifiableMap(tree);
    }

    private static Object convert(Object value) {
        if (value instanceof ConfigurationSection nested) {
            return toMap(nested);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> tree = new LinkedHashMap<>();
            map.forEach((key, element) -> tree.put(String.valueOf(key), convert(element)));
            return Collections.unmodifiableMap(tree);
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object element : list) {
                copy.add(convert(element));
            }
            return Collections.unmodifiableList(copy);
        }
        return value;
    }
}
