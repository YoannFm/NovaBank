/**
 * Interface de fournisseur (SPI) des backends de stockage : un backend regroupe les ports de
 * persistance du coeur et se cree par une fabrique a partir de parametres neutres.
 *
 * <p>Sert aux backends livres en module embarque (charges dans un chargeur isole avec leurs
 * pilotes, decision D18) : le module ne voit du plugin que l'API et le coeur.
 */
package dev.novadev.novabank.core.storage;
