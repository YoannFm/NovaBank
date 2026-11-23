package dev.novadev.novabank.config.tree;

import dev.novadev.novabank.config.model.Secret;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Seul endroit qui declare les chemins secrets de la configuration (D5).
 *
 * <p>Un chemin secret, et tout chemin situe sous lui, n'est modifiable que dans le
 * fichier (jamais en jeu) et n'est affiche que masque. La lecture d'un secret par
 * {@link ConfigNode#secret(String, String)} echoue si son chemin n'est pas declare ici,
 * ce qui garde cette liste synchronisee avec les parseurs.
 */
public final class SecretPaths {

    /** Chemins secrets. */
    public static final Set<String> ALL = Set.of(
            "storage.sql.username",
            "storage.sql.password",
            "storage.mongodb.uri",
            "redis.password",
            "discord.webhook-url",
            "discord.bot-token");

    private SecretPaths() {
    }

    /**
     * Indique si un chemin est secret ou situe sous un chemin secret.
     *
     * @param path chemin teste
     * @return {@code true} si sa valeur ne doit ni s'afficher ni se modifier en jeu
     */
    public static boolean isSecret(String path) {
        return ALL.stream().anyMatch(secret -> path.equals(secret) || path.startsWith(secret + ConfigTrees.SEPARATOR));
    }

    /**
     * Indique si modifier ce chemin toucherait un secret : chemin secret, sous un secret,
     * ou section contenant un secret (remplacer {@code storage.sql} entier, par exemple).
     *
     * @param path chemin teste
     * @return {@code true} si la modification doit etre refusee en jeu
     */
    public static boolean touchesSecret(String path) {
        return isSecret(path) || ALL.stream().anyMatch(secret -> secret.startsWith(path + ConfigTrees.SEPARATOR));
    }

    /**
     * Valeur affichable d'une cle.
     *
     * @param path  chemin de la cle
     * @param value valeur reelle
     * @return {@value Secret#MASK} si le chemin est secret, sinon la valeur
     */
    public static Object mask(String path, Object value) {
        return isSecret(path) ? Secret.MASK : value;
    }

    /**
     * Copie non modifiable d'un arbre ou chaque valeur secrete presente est remplacee par
     * {@value Secret#MASK}, pour l'affichage ou les logs.
     *
     * @param tree arbre source, inchange
     * @return l'arbre masque
     */
    public static Map<String, Object> maskTree(Map<?, ?> tree) {
        return maskMap(tree, "");
    }

    private static Map<String, Object> maskMap(Map<?, ?> map, String parent) {
        Map<String, Object> copy = new LinkedHashMap<>();
        map.forEach((key, value) -> {
            String path = ConfigTrees.child(parent, String.valueOf(key));
            copy.put(String.valueOf(key), maskValue(path, value));
        });
        return Collections.unmodifiableMap(copy);
    }

    private static Object maskValue(String path, Object value) {
        if (value == null) {
            return null;
        }
        if (isSecret(path)) {
            return Secret.MASK;
        }
        if (value instanceof Map<?, ?> map) {
            return maskMap(map, path);
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (int i = 0; i < list.size(); i++) {
                copy.add(maskValue(ConfigTrees.element(path, i), list.get(i)));
            }
            return Collections.unmodifiableList(copy);
        }
        return value;
    }
}
