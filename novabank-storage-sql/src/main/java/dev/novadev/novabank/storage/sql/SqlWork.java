package dev.novadev.novabank.storage.sql;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Travail execute sur une connexion fournie par {@link SqlTransactions}.
 *
 * @param <T> type du resultat
 */
@FunctionalInterface
interface SqlWork<T> {

    /**
     * @param connection connexion, en transaction pour une ecriture
     * @return le resultat
     * @throws SQLException erreur du pilote
     */
    T run(Connection connection) throws SQLException;
}
