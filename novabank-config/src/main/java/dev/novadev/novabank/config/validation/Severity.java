package dev.novadev.novabank.config.validation;

import dev.novadev.novabank.core.text.ConsoleText;

/**
 * Gravite d'une issue de validation.
 */
public enum Severity {

    /** Bloquante : la configuration n'est pas chargee. */
    ERROR,

    /** Non bloquante : la valeur est corrigee ou ignoree, l'administrateur est prevenu. */
    WARNING;

    /**
     * Libelle francais affiche dans la console.
     *
     * @return le libelle
     */
    public String label() {
        return this == ERROR ? ConsoleText.tr("ERREUR") : ConsoleText.tr("AVERTISSEMENT");
    }
}
