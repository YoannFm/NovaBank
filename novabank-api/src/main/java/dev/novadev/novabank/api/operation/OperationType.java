package dev.novadev.novabank.api.operation;

/**
 * Nature d'une operation bancaire.
 */
public enum OperationType {

    /** Depot d'items dans une banque. */
    DEPOSIT,

    /** Retrait d'items d'une banque (reserve puis confirme ou annule). */
    WITHDRAW,

    /** Reinitialisation de la valeur d'une banque, sans toucher au stock. */
    RESET
}
