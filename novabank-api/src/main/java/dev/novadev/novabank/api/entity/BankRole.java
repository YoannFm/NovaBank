package dev.novadev.novabank.api.entity;

/**
 * Role d'un joueur dans l'entite proprietaire d'une banque.
 *
 * <p>Chaque fournisseur d'appartenance traduit la hierarchie de son plugin (chef d'ile,
 * officier de faction, resident de ville...) vers ces trois roles. Les droits de depot et
 * de retrait de chaque role sont fixes par l'administrateur, par type d'entite.
 */
public enum BankRole {

    /** Proprietaire ou chef de l'entite. */
    OWNER,

    /** Gestionnaire de l'entite (officier, adjoint, moderateur...). */
    ADMIN,

    /** Simple membre de l'entite. */
    MEMBER
}
