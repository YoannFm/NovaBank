package dev.novadev.novabank.api.valuation;

/**
 * Mode de calcul de la valeur d'une banque, choisi par l'administrateur.
 *
 * <p>Dans les deux modes, seules les unites <em>comptees</em> ont une valeur : apres
 * une reinitialisation, les blocs deja presents restent dans la banque mais ne
 * valent plus rien, et les retirer puis les redeposer ne regenere aucune valeur.
 */
public enum ValuationMode {

    /**
     * Valeur derivee : unites comptees x prix courant. Un changement de prix se
     * repercute immediatement sur toutes les banques ; un item retire du catalogue
     * vaut zero (son stock reste retirable).
     */
    DERIVED,

    /**
     * Valeur cumulee : chaque depot ajoute la valeur des unites comptees au prix du
     * moment, chaque retrait deduit leur valeur moyenne. Un changement de prix n'a
     * aucun effet retroactif.
     */
    ACCUMULATED
}
