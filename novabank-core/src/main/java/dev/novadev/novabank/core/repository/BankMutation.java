package dev.novadev.novabank.core.repository;

import dev.novadev.novabank.core.bank.Bank;

/**
 * Mutation d'une banque pour une operation nouvelle (depot, reservation, reset).
 *
 * <p>Doit etre une fonction pure de son argument : un backend a verrou optimiste peut
 * l'appeler plusieurs fois, seule la decision prise sur l'etat finalement ecrit compte.
 */
@FunctionalInterface
public interface BankMutation {

    /**
     * @param current etat courant de la banque, lu dans l'unite atomique
     * @return la decision a appliquer
     */
    MutationDecision decide(Bank current);
}
