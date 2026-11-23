package dev.novadev.novabank.bukkit.config;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Brouillon du menu des parametres d'un administrateur : valeurs lues dans {@code config.yml} et changements en attente.
 * Rien n'est ecrit avant l'enregistrement, qui applique les changements au texte du fichier ({@link #applyTo}) en
 * conservant ses commentaires. Valeurs normalisees : {@link Boolean}, {@link Long}, {@link BigDecimal}, {@link String},
 * {@code List<String>}, {@code Map<String, String>}. Sans dependance serveur ; utilise sur le thread du joueur.
 */
public final class SettingsDraft {

    /** Roles d'une banque, dans l'ordre d'affichage. */
    public static final List<String> ROLES = List.of("owner", "admin", "member");

    private static final String TYPES = "banks.entity-types";
    private static final String PRIORITY = "banks.default-priority";
    private static final Map<String, Object> LEADERBOARD_DEFAULTS = Map.of("enabled", true, "size", 10L,
            "refresh-seconds", 60L, "per-category", false);
    private static final BigDecimal HALF = new BigDecimal("0.5");

    private final Map<String, Object> tree;
    private final Map<String, Object> changes = new LinkedHashMap<>();

    /**
     * @param tree contenu actuel de {@code config.yml} (tables imbriquees)
     */
    public SettingsDraft(Map<String, Object> tree) {
        this.tree = Map.copyOf(Objects.requireNonNull(tree, "tree"));
    }

    /**
     * @param path chemin d'un reglage
     * @return sa valeur dans le brouillon, sinon dans le fichier, sinon sa valeur par defaut connue
     */
    public Optional<Object> value(String path) {
        if (changes.containsKey(path)) {
            return Optional.of(changes.get(path));
        }
        return original(path);
    }

    /**
     * @param path chemin d'un reglage
     * @return {@code true} si le brouillon le modifie
     */
    public boolean changed(String path) {
        return changes.containsKey(path);
    }

    /** @return {@code true} si le brouillon differe du fichier */
    public boolean dirty() {
        return !changes.isEmpty();
    }

    /** @return nombre de reglages modifies */
    public int changeCount() {
        return changes.size();
    }

    /**
     * Change un reglage ; revenir a la valeur du fichier annule le changement.
     *
     * @param path  chemin
     * @param value valeur normalisee
     */
    public void set(String path, Object value) {
        Object normalized = normalize(Objects.requireNonNull(value, "value"));
        if (normalized.equals(original(path).orElse(null))) {
            changes.remove(path);
        } else {
            changes.put(path, normalized);
        }
    }

    /** @return sections de premier niveau touchees par le brouillon */
    public Set<String> sections() {
        Set<String> sections = new LinkedHashSet<>();
        changes.keySet().forEach(path -> sections.add(path.contains(".") ? path.substring(0, path.indexOf('.')) : path));
        return sections;
    }

    /**
     * @param yaml texte actuel de {@code config.yml}
     * @return le texte avec les changements du brouillon (commentaires conserves)
     */
    public String applyTo(String yaml) {
        String result = yaml;
        for (Map.Entry<String, Object> change : changes.entrySet()) {
            result = YamlPaths.set(result, change.getKey(), format(change.getValue()));
        }
        return result;
    }

    // ------------------------------------------------------------------ lectures typees

    /**
     * @param path chemin
     * @return le booleen (faux si absent)
     */
    public boolean bool(String path) {
        return value(path).map(Boolean.TRUE::equals).orElse(false);
    }

    /**
     * @param path chemin
     * @return le nombre (zero si absent ou non numerique)
     */
    public BigDecimal number(String path) {
        return value(path).filter(BigDecimal.class::isInstance).map(BigDecimal.class::cast)
                .or(() -> value(path).filter(Long.class::isInstance).map(v -> BigDecimal.valueOf((Long) v)))
                .orElse(BigDecimal.ZERO);
    }

    /**
     * @param path chemin
     * @return le texte (vide si absent)
     */
    public String text(String path) {
        return value(path).filter(v -> !(v instanceof List<?>) && !(v instanceof Map<?, ?>)).map(String::valueOf).orElse("");
    }

    /**
     * @param path chemin
     * @return la liste (vide si absente)
     */
    @SuppressWarnings("unchecked")
    public List<String> list(String path) {
        return value(path).filter(List.class::isInstance).map(v -> (List<String>) v).orElse(List.of());
    }

    /**
     * @param path chemin
     * @return la table (vide si absente)
     */
    @SuppressWarnings("unchecked")
    public Map<String, String> map(String path) {
        return value(path).filter(Map.class::isInstance).map(v -> (Map<String, String>) v).orElse(Map.of());
    }

    // ------------------------------------------------------------------ actions des ecrans

    /**
     * Entier : ajoute {@code delta}, borne par le reglage.
     *
     * @param setting reglage {@link Setting.Kind#INTEGER} ou {@link Setting.Kind#DECIMAL}
     * @param steps   nombre de pas (negatif pour diminuer)
     */
    public void step(Setting setting, int steps) {
        BigDecimal step = setting.kind() == Setting.Kind.DECIMAL ? HALF : BigDecimal.valueOf(setting.step());
        BigDecimal next = number(setting.path()).add(step.multiply(BigDecimal.valueOf(steps)))
                .max(BigDecimal.valueOf(setting.min())).min(BigDecimal.valueOf(setting.max()));
        set(setting.path(), setting.kind() == Setting.Kind.DECIMAL ? next : (Object) next.longValue());
    }

    /**
     * Choix : valeur suivante ({@code +1}) ou precedente ({@code -1}).
     *
     * @param setting   reglage {@link Setting.Kind#CHOICE}
     * @param direction +1 ou -1
     */
    public void cycle(Setting setting, int direction) {
        List<String> choices = setting.choices();
        int index = choices.indexOf(text(setting.path()));
        int next = Math.floorMod(index < 0 ? 0 : index + direction, choices.size());
        set(setting.path(), choices.get(next));
    }

    /** @return types de banques actifs du brouillon */
    public List<String> types() {
        return list(TYPES);
    }

    /** @return ordre de choix par defaut du brouillon */
    public List<String> priority() {
        return list(PRIORITY);
    }

    /**
     * Active ou desactive un type ; un type desactive quitte aussi l'ordre de choix par defaut (regle de validation).
     *
     * @param type type d'entite
     */
    public void toggleType(String type) {
        List<String> types = new ArrayList<>(types());
        if (!types.remove(type)) {
            types.add(type);
        }
        set(TYPES, types);
        List<String> priority = new ArrayList<>(priority());
        if (priority.retainAll(types)) {
            set(PRIORITY, priority);
        }
    }

    /**
     * Ajoute un type actif a la fin de l'ordre de choix, ou l'en retire.
     *
     * @param type type d'entite actif
     */
    public void togglePriority(String type) {
        List<String> priority = new ArrayList<>(priority());
        if (!priority.remove(type) && types().contains(type)) {
            priority.add(type);
        }
        set(PRIORITY, priority);
    }

    /**
     * @param type     type d'entite
     * @param withdraw {@code true} pour le retrait, sinon le depot
     * @return les roles autorises (valeur par defaut de NovaBank si le fichier ne dit rien)
     */
    public List<String> roles(String type, boolean withdraw) {
        return list(rolesPath(type, withdraw));
    }

    /**
     * Autorise ou retire un role pour le depot ou le retrait d'un type (ordre owner, admin, member conserve).
     *
     * @param type     type d'entite
     * @param withdraw {@code true} pour le retrait
     * @param role     {@code owner}, {@code admin} ou {@code member}
     */
    public void toggleRole(String type, boolean withdraw, String role) {
        List<String> current = roles(type, withdraw);
        List<String> next = new ArrayList<>();
        for (String candidate : ROLES) {
            if (candidate.equals(role) != current.contains(candidate)) {
                next.add(candidate);
            }
        }
        set(rolesPath(type, withdraw), next);
    }

    private static String rolesPath(String type, boolean withdraw) {
        return "banks.roles." + type + (withdraw ? ".withdraw" : ".deposit");
    }

    // ------------------------------------------------------------------ fichier

    private Optional<Object> original(String path) {
        Object node = tree;
        for (String part : path.split("\\.")) {
            if (!(node instanceof Map<?, ?> map) || !map.containsKey(part)) {
                return defaultValue(path);
            }
            node = map.get(part);
        }
        return node == null ? defaultValue(path) : Optional.of(normalize(node));
    }

    /** Valeurs par defaut des analyseurs pour les cles souvent absentes du fichier (classements et roles par type). */
    private static Optional<Object> defaultValue(String path) {
        String[] parts = path.split("\\.");
        if (parts.length == 3 && parts[0].equals("leaderboards")) {
            return Optional.ofNullable(LEADERBOARD_DEFAULTS.get(parts[2]));
        }
        if (parts.length == 4 && path.startsWith("banks.roles.")) {
            return Optional.of(parts[3].equals("withdraw") ? List.of("owner", "admin") : ROLES);
        }
        return Optional.empty();
    }

    private static Object normalize(Object value) {
        if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte) {
            return ((Number) value).longValue();
        }
        if (value instanceof Double || value instanceof Float) {
            return new BigDecimal(value.toString()).stripTrailingZeros();
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros();
        }
        if (value instanceof Collection<?> items) {
            return items.stream().map(String::valueOf).toList();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, String> copy = new TreeMap<>();
            map.forEach((key, item) -> copy.put(String.valueOf(key), String.valueOf(item)));
            return Map.copyOf(copy);
        }
        return value;
    }

    /**
     * @param value valeur normalisee
     * @return son ecriture YAML en ligne
     */
    static String format(Object value) {
        if (value instanceof Boolean || value instanceof Long) {
            return value.toString();
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.toPlainString();
        }
        if (value instanceof List<?> items) {
            return YamlPaths.inlineList(items.stream().map(item -> YamlPaths.quote(String.valueOf(item))).toList());
        }
        if (value instanceof Map<?, ?> map) {
            List<String> entries = new ArrayList<>();
            map.forEach((key, item) -> entries.add(key + ": " + YamlPaths.quote(String.valueOf(item))));
            entries.sort(null);
            return "{" + String.join(", ", entries) + "}";
        }
        return YamlPaths.quote(String.valueOf(value));
    }
}
