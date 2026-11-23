package dev.novadev.novabank.bukkit.config;

import java.util.List;
import java.util.Objects;

/**
 * Un reglage de {@code config.yml} modifiable dans le menu des parametres.
 *
 * @param path    chemin de la cle ({@code limits.cooldown-ms})
 * @param kind    nature du reglage, qui decide du clic
 * @param icon    icone (item {@code minecraft:} sans namespace)
 * @param restart {@code true} si le reglage ne s'applique qu'au redemarrage du serveur
 * @param choices valeurs possibles d'un {@link Kind#CHOICE}, vide sinon
 * @param min     minimum d'un nombre
 * @param max     maximum d'un nombre
 * @param step    pas d'un clic sur un nombre
 */
public record Setting(String path, Kind kind, String icon, boolean restart, List<String> choices, long min, long max,
                      long step) {

    /** Nature d'un reglage. */
    public enum Kind {
        /** Oui / non : un clic inverse. */
        BOOLEAN,
        /** Une valeur parmi {@link #choices} : gauche suivante, droite precedente. */
        CHOICE,
        /** Entier borne : gauche +pas, droite -pas, majuscule saisie au clavier. */
        INTEGER,
        /** Decimal positif (pas de 0,5) : comme un entier. */
        DECIMAL,
        /** Texte saisi au clavier ({@code -} : vide). */
        TEXT,
        /** Texte secret saisi au clavier, jamais affiche. */
        SECRET,
        /** Liste de textes saisie au clavier, separes par des virgules. */
        TEXT_LIST,
        /** Table {@code cle=valeur} saisie au clavier, separees par des points-virgules. */
        TEXT_MAP,
        /** Types de banques actifs : ecran dedie. */
        TYPES,
        /** Ordre de choix de la banque active : ecran dedie. */
        PRIORITY,
        /** Droits des roles par type : ecran dedie. */
        ROLES
    }

    /**
     * @throws NullPointerException si un champ objet est nul
     */
    public Setting {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(icon, "icon");
        choices = List.copyOf(choices);
    }

    static Setting of(String path, Kind kind, String icon, boolean restart) {
        return new Setting(path, kind, icon, restart, List.of(), 0, 0, 0);
    }

    static Setting choice(String path, String icon, boolean restart, String... choices) {
        return new Setting(path, Kind.CHOICE, icon, restart, List.of(choices), 0, 0, 0);
    }

    static Setting integer(String path, String icon, boolean restart, long min, long max, long step) {
        return new Setting(path, Kind.INTEGER, icon, restart, List.of(), min, max, step);
    }

    /**
     * @return la cle de premier niveau du reglage ({@code limits})
     */
    public String section() {
        int dot = path.indexOf('.');
        return dot < 0 ? path : path.substring(0, dot);
    }

    /** Reglages repetes par type de banque ou par hote : textes communs, la partie variable devient {@code {type}}. */
    private static final String LEADERBOARDS = "leaderboards.";
    private static final String HOSTS = "commands.hosts.";

    /**
     * @return la cle des textes du reglage dans les fichiers de langue (type d'un classement, hote : generiques)
     */
    public String textKey() {
        if (path.startsWith(LEADERBOARDS)) {
            return LEADERBOARDS + "type" + path.substring(path.indexOf('.', LEADERBOARDS.length()));
        }
        if (path.startsWith(HOSTS)) {
            return HOSTS + "host" + path.substring(path.indexOf('.', HOSTS.length()));
        }
        return path;
    }

    /**
     * @return la partie variable du chemin, pour {@code {type}} dans les textes ({@code island}, {@code towny}), ou vide
     */
    public String variable() {
        String prefix = path.startsWith(LEADERBOARDS) ? LEADERBOARDS : path.startsWith(HOSTS) ? HOSTS : null;
        return prefix == null ? "" : path.substring(prefix.length(), path.indexOf('.', prefix.length()));
    }
}
