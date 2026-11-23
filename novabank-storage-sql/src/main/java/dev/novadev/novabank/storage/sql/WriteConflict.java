package dev.novadev.novabank.storage.sql;

/**
 * Conflit d'ecriture detecte dans une transaction (version de banque changee entre la
 * lecture et l'ecriture, identifiant d'operation insere entre-temps) : la transaction
 * est annulee puis rejouee depuis le debut.
 */
final class WriteConflict extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param message nature du conflit
     */
    WriteConflict(String message) {
        super(message, null, false, false);
    }
}
