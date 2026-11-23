package dev.novadev.novabank.storage.mongo;

import com.mongodb.MongoException;

/**
 * Tentative annulee sur une erreur transitoire du serveur (etiquette
 * {@code TransientTransactionError} : conflit d'ecriture, election, coupure reseau en cours de
 * transaction). Rien n'a ete ecrit ; {@link Contention} la rejoue un nombre limite de fois, puis
 * remonte l'erreur d'origine (panne, et non conflit).
 */
final class TransientFailure extends RuntimeException {

    private static final long serialVersionUID = 1L;

    TransientFailure(MongoException cause) {
        super(cause.getMessage(), cause);
    }

    @Override
    public synchronized MongoException getCause() {
        return (MongoException) super.getCause();
    }
}
