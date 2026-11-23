package dev.novadev.novabank.config.tree;

import dev.novadev.novabank.config.model.Secret;
import dev.novadev.novabank.core.text.ConsoleText;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Lecture typee d'une section de l'arbre de configuration.
 *
 * <p>Aucune methode ne leve d'exception pour une valeur invalide : l'issue est ajoutee
 * au rapport du {@link ParseContext} avec le chemin complet de la cle, et la valeur par
 * defaut est renvoyee pour que la lecture continue et accumule toutes les issues.
 * Une cle absente ou de valeur {@code null} prend sa valeur par defaut, sans issue.
 *
 * <p>Conversions acceptees : nombres en {@code Integer}, {@code Long}, {@code Double},
 * {@code BigDecimal}... ou en texte (lu exactement, sans passer par {@code double}) ;
 * booleens en {@code Boolean} ou texte {@code true}/{@code false} ; textes depuis tout
 * scalaire. Les valeurs des chemins secrets ne sont jamais recopiees dans les messages.
 */
public final class ConfigNode {


    private final Map<?, ?> values;
    private final String path;
    private final ParseContext context;
    private final Map<String, ConfigNode> sections = new HashMap<>();

    private ConfigNode(Map<?, ?> values, String path, ParseContext context) {
        this.values = values;
        this.path = path;
        this.context = context;
    }

    private static ConfigNode create(Map<?, ?> values, String path, ParseContext context) {
        ConfigNode node = new ConfigNode(values, path, context);
        context.register(node);
        return node;
    }

    /**
     * Noeud racine d'un arbre.
     *
     * @param tree    arbre lu
     * @param context etat de lecture partage
     * @return le noeud racine (chemin vide)
     */
    public static ConfigNode root(Map<?, ?> tree, ParseContext context) {
        return create(tree, "", context);
    }

    /**
     * Chemin complet de ce noeud.
     *
     * @return le chemin, vide pour la racine
     */
    public String path() {
        return path;
    }

    /**
     * Chemin complet d'une cle de ce noeud.
     *
     * @param key cle (eventuellement suivie d'un index {@code [i]})
     * @return le chemin complet
     */
    public String pathOf(String key) {
        return ConfigTrees.child(path, key);
    }

    /**
     * Indique si une cle porte une valeur non nulle. Ne marque pas la cle comme lue.
     *
     * @param key cle
     * @return {@code true} si la cle est renseignee
     */
    public boolean has(String key) {
        return values.get(key) != null;
    }

    /**
     * Cles presentes dans ce noeud, dans l'ordre du fichier. Ne les marque pas comme lues.
     *
     * @return les cles
     */
    public Set<String> keys() {
        Set<String> keys = new LinkedHashSet<>();
        values.keySet().forEach(key -> keys.add(String.valueOf(key)));
        return keys;
    }

    /**
     * Sous-section. Absente : section vide (toutes ses cles prendront leur valeur par
     * defaut). Pas une section : erreur, puis section vide.
     *
     * @param key cle de la sous-section
     * @return le noeud de la sous-section (meme instance a chaque appel)
     */
    public ConfigNode section(String key) {
        ConfigNode cached = sections.get(key);
        if (cached != null) {
            return cached;
        }
        Object raw = read(key);
        Map<?, ?> map = Map.of();
        if (raw instanceof Map<?, ?> found) {
            map = found;
        } else if (raw != null) {
            error(key, ConsoleText.tr("section attendue (cle: valeur), recu : {0}", describe(key, raw)));
        }
        ConfigNode node = create(map, pathOf(key), context);
        sections.put(key, node);
        return node;
    }

    /**
     * Liste de sections, chacune de chemin {@code key[i]}. Un element qui n'est pas une
     * section est signale et ecarte.
     *
     * @param key cle de la liste
     * @return les sections valides, vide si la cle est absente
     */
    public List<ConfigNode> sectionList(String key) {
        Optional<List<?>> list = list(key);
        List<ConfigNode> nodes = new ArrayList<>();
        list.ifPresent(elements -> {
            for (int i = 0; i < elements.size(); i++) {
                String elementPath = ConfigTrees.element(pathOf(key), i);
                if (elements.get(i) instanceof Map<?, ?> map) {
                    nodes.add(create(map, elementPath, context));
                } else {
                    context.report().error(elementPath, ConsoleText.tr("section attendue (cle: valeur)"));
                }
            }
        });
        return nodes;
    }

    /**
     * Texte.
     *
     * @param key          cle
     * @param defaultValue valeur si absente ou invalide
     * @return le texte lu
     */
    public String string(String key, String defaultValue) {
        return text(key).orElse(defaultValue);
    }

    /**
     * Texte qui doit respecter un motif.
     *
     * @param key          cle
     * @param defaultValue valeur si absente ou invalide (supposee valide)
     * @param pattern      motif impose a la valeur entiere
     * @param expected     description du format attendu, pour le message d'erreur
     * @return le texte lu
     */
    public String matching(String key, String defaultValue, Pattern pattern, String expected) {
        return optionalMatching(key, pattern, expected).orElse(defaultValue);
    }

    /**
     * Texte optionnel qui doit respecter un motif.
     *
     * @param key      cle
     * @param pattern  motif impose a la valeur entiere
     * @param expected description du format attendu
     * @return le texte, ou vide s'il est absent ou invalide
     */
    public Optional<String> optionalMatching(String key, Pattern pattern, String expected) {
        return text(key).filter(text -> {
            boolean valid = pattern.matcher(text).matches();
            if (!valid) {
                error(key, ConsoleText.tr("valeur invalide {0} (attendu : {1})", describe(key, text), expected));
            }
            return valid;
        });
    }

    /**
     * Texte optionnel ; une valeur vide ou faite d'espaces est consideree absente.
     *
     * @param key cle
     * @return le texte, ou vide
     */
    public Optional<String> optionalText(String key) {
        return text(key).filter(text -> !text.isBlank());
    }

    /**
     * Texte obligatoire.
     *
     * @param key cle
     * @return le texte, ou vide (erreur ajoutee) s'il est absent ou invalide
     */
    public Optional<String> requiredString(String key) {
        if (!has(key)) {
            read(key);
            error(key, ConsoleText.tr("cle obligatoire manquante"));
            return Optional.empty();
        }
        return text(key);
    }

    /**
     * Valeur secrete. Le chemin doit etre declare dans {@link SecretPaths}.
     *
     * @param key          cle
     * @param defaultValue valeur si absente ou invalide
     * @return le secret
     * @throws IllegalStateException si le chemin n'est pas declare secret (erreur de programmation)
     */
    public Secret secret(String key, String defaultValue) {
        String secretPath = pathOf(key);
        if (!SecretPaths.ALL.contains(secretPath)) {
            throw new IllegalStateException(ConsoleText.tr("Chemin lu comme secret mais absent de SecretPaths : {0}",
                    secretPath));
        }
        return Secret.of(string(key, defaultValue));
    }

    /**
     * Booleen.
     *
     * @param key          cle
     * @param defaultValue valeur si absente ou invalide
     * @return le booleen lu
     */
    public boolean bool(String key, boolean defaultValue) {
        Object raw = read(key);
        if (raw == null) {
            return defaultValue;
        }
        if (raw instanceof Boolean value) {
            return value;
        }
        if (raw instanceof String text) {
            String trimmed = text.trim();
            if (trimmed.equalsIgnoreCase("true") || trimmed.equalsIgnoreCase("false")) {
                return Boolean.parseBoolean(trimmed);
            }
        }
        error(key, ConsoleText.tr("booleen attendu (true ou false), recu : {0}", describe(key, raw)));
        return defaultValue;
    }

    /**
     * Entier borne.
     *
     * @param key          cle
     * @param defaultValue valeur si absente ou invalide
     * @param min          borne basse incluse
     * @param max          borne haute incluse
     * @return l'entier lu
     */
    public int intValue(String key, int defaultValue, int min, int max) {
        return (int) longValue(key, defaultValue, min, max);
    }

    /**
     * Entier long borne. Un nombre decimal sans partie fractionnaire ({@code 25.0}) est accepte.
     *
     * @param key          cle
     * @param defaultValue valeur si absente ou invalide
     * @param min          borne basse incluse
     * @param max          borne haute incluse
     * @return l'entier lu
     */
    public long longValue(String key, long defaultValue, long min, long max) {
        Object raw = read(key);
        if (raw == null) {
            return defaultValue;
        }
        Optional<Long> parsed = Scalars.toDecimal(raw).flatMap(Scalars::exactLong);
        if (parsed.isEmpty()) {
            error(key, ConsoleText.tr("nombre entier attendu, recu : {0}", describe(key, raw)));
            return defaultValue;
        }
        long value = parsed.get();
        if (value < min || value > max) {
            error(key, ConsoleText.tr("doit etre {0} (recu : {1})", Scalars.rangeText(min, max), value));
            return defaultValue;
        }
        return value;
    }

    /**
     * Nombre decimal obligatoire, lu exactement : un texte comme {@code "0.1"} donne
     * exactement {@code 0.1}.
     *
     * @param key cle
     * @return le nombre, ou vide (erreur ajoutee) s'il est absent ou invalide
     */
    public Optional<BigDecimal> requiredDecimal(String key) {
        Object raw = read(key);
        if (raw == null) {
            error(key, ConsoleText.tr("cle obligatoire manquante"));
            return Optional.empty();
        }
        Optional<BigDecimal> parsed = Scalars.toDecimal(raw);
        if (parsed.isEmpty()) {
            error(key, ConsoleText.tr("nombre attendu, recu : {0}", describe(key, raw)));
        }
        return parsed;
    }

    /**
     * Constante d'enumeration, sans tenir compte de la casse ; {@code -} vaut {@code _}.
     *
     * @param key          cle
     * @param type         type de l'enumeration
     * @param defaultValue valeur si absente ou invalide
     * @param <E>          type de l'enumeration
     * @return la constante lue
     */
    public <E extends Enum<E>> E enumValue(String key, Class<E> type, E defaultValue) {
        Optional<String> text = text(key);
        if (text.isEmpty()) {
            return defaultValue;
        }
        String name = text.get().trim().toUpperCase(Locale.ROOT).replace('-', '_');
        E[] constants = type.getEnumConstants();
        for (E constant : constants) {
            if (constant.name().equals(name)) {
                return constant;
            }
        }
        error(key, ConsoleText.tr("valeur invalide {0} (valeurs possibles : {1})",
                describe(key, text.get()), Arrays.toString(constants)));
        return defaultValue;
    }

    /**
     * Liste de textes (espaces de debut et fin retires). Un element non scalaire est
     * signale a son chemin {@code key[i]} et ecarte.
     *
     * @param key          cle
     * @param defaultValue valeur si absente ou si la valeur n'est pas une liste
     * @return la liste lue, non modifiable
     */
    public List<String> stringList(String key, List<String> defaultValue) {
        Optional<List<?>> list = list(key);
        if (list.isEmpty()) {
            return defaultValue;
        }
        List<String> texts = new ArrayList<>();
        List<?> elements = list.get();
        for (int i = 0; i < elements.size(); i++) {
            String text = Scalars.scalarText(elements.get(i));
            if (text == null) {
                error(ConfigTrees.element(key, i), ConsoleText.tr("texte attendu, recu : {0}",
                        describe(key, elements.get(i))));
            } else {
                texts.add(text.trim());
            }
        }
        return List.copyOf(texts);
    }

    /**
     * Section libre de textes (cles quelconques), par exemple des options JDBC. Toute
     * cle situee sous ce chemin est reconnue.
     *
     * @param key cle de la section
     * @return les paires lues, dans l'ordre du fichier, non modifiables
     */
    public Map<String, String> stringMap(String key) {
        Object raw = read(key);
        context.markFree(pathOf(key));
        if (raw == null) {
            return Map.of();
        }
        if (!(raw instanceof Map<?, ?> map)) {
            error(key, ConsoleText.tr("section attendue (cle: valeur), recu : {0}", describe(key, raw)));
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        map.forEach((entryKey, value) -> {
            String entryPath = ConfigTrees.child(key, String.valueOf(entryKey));
            String text = Scalars.scalarText(value);
            if (text == null) {
                error(entryPath, ConsoleText.tr("texte attendu, recu : {0}", describe(entryPath, value)));
            } else {
                result.put(String.valueOf(entryKey), text);
            }
        });
        return Collections.unmodifiableMap(result);
    }

    /**
     * Ecarte une cle avec un avertissement explicite, qui remplace l'avertissement
     * generique "cle inconnue". Son contenu n'est pas lu, donc pas reconnu.
     *
     * @param key     cle ecartee
     * @param message raison
     */
    public void ignore(String key, String message) {
        read(key);
        warning(key, message);
    }

    /**
     * Ajoute une erreur bloquante sur une cle de ce noeud.
     *
     * @param key     cle (eventuellement suivie d'un index {@code [i]})
     * @param message explication
     */
    public void error(String key, String message) {
        context.report().error(pathOf(key), message);
    }

    /**
     * Ajoute un avertissement sur une cle de ce noeud.
     *
     * @param key     cle (eventuellement suivie d'un index {@code [i]})
     * @param message explication
     */
    public void warning(String key, String message) {
        context.report().warning(pathOf(key), message);
    }

    private Object read(String key) {
        context.markRead(pathOf(key));
        return values.get(key);
    }

    private Optional<String> text(String key) {
        Object raw = read(key);
        if (raw == null) {
            return Optional.empty();
        }
        String text = Scalars.scalarText(raw);
        if (text == null) {
            error(key, ConsoleText.tr("texte attendu, recu : {0}", describe(key, raw)));
        }
        return Optional.ofNullable(text);
    }

    private Optional<List<?>> list(String key) {
        Object raw = read(key);
        if (raw == null) {
            return Optional.empty();
        }
        if (raw instanceof List<?> list) {
            return Optional.of(list);
        }
        error(key, ConsoleText.tr("liste attendue, recu : {0}", describe(key, raw)));
        return Optional.empty();
    }

    private String describe(String key, Object value) {
        if (SecretPaths.isSecret(pathOf(key))) {
            return Secret.MASK;
        }
        if (value instanceof Map) {
            return ConsoleText.tr("une section");
        }
        if (value instanceof List) {
            return ConsoleText.tr("une liste");
        }
        return "'" + value + "'";
    }
}
