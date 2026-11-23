package dev.novadev.novabank.config.section;

import java.util.regex.Pattern;

/**
 * Formats de valeurs partages par plusieurs sections.
 */
final class ConfigPatterns {

    /** Identifiant technique : serveur, groupe, categorie. */
    static final Pattern IDENTIFIER = Pattern.compile("[a-z0-9_-]+");
    static final String IDENTIFIER_HINT = "minuscules, chiffres, '_' ou '-'";

    /** Texte non vide (au moins un caractere visible). */
    static final Pattern NOT_BLANK = Pattern.compile("(?s).*\\S.*");
    static final String NOT_BLANK_HINT = "texte non vide";

    private ConfigPatterns() {
    }
}
