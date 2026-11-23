package dev.novadev.novabank.core.application;

/**
 * Issue d'une consultation ou d'un choix de banque active.
 */
public enum QueryStatus {

    /** Consultation ou choix effectue. */
    OK,
    /** Permission manquante. */
    NO_PERMISSION,
    /** Le joueur n'appartient a aucune banque. */
    NO_BANK,
    /** Type d'entite inconnu ou non active. */
    UNKNOWN_TYPE,
    /** Le joueur n'appartient a aucune entite de ce type. */
    NOT_MEMBER,
    /** Base de donnees indisponible. */
    STORAGE_ERROR
}
