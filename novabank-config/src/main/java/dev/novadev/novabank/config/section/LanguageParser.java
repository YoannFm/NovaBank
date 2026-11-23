package dev.novadev.novabank.config.section;

import dev.novadev.novabank.config.tree.ConfigNode;
import java.util.regex.Pattern;

/**
 * Cle {@code language} : langue des messages, nom du fichier {@code lang/<langue>.yml}.
 */
public final class LanguageParser {

    /** Langue par defaut. */
    public static final String DEFAULT = "fr";

    private static final Pattern LANGUAGE = Pattern.compile("[a-z]{2}(_[a-z]{2})?");

    private LanguageParser() {
    }

    /**
     * Lit et valide la cle.
     *
     * @param root racine de la configuration
     * @return le code de langue, par exemple {@code fr} ou {@code en}
     */
    public static String parse(ConfigNode root) {
        return root.matching("language", DEFAULT, LANGUAGE, "code de langue, par exemple fr, en ou pt_br");
    }
}
