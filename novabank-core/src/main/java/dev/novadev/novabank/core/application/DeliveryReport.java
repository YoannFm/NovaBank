package dev.novadev.novabank.core.application;

/**
 * Resultat de la remise des livraisons en attente d'un joueur.
 *
 * @param delivered unites remises
 * @param remaining unites encore dues (inventaire plein, fournisseur d'item absent...)
 */
public record DeliveryReport(long delivered, long remaining) {

    /** Rien a remettre. */
    public static final DeliveryReport NOTHING = new DeliveryReport(0L, 0L);
}
