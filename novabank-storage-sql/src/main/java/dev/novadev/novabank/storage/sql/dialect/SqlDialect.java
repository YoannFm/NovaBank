package dev.novadev.novabank.storage.sql.dialect;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * Seul endroit ou vivent les differences entre moteurs SQL : types, upsert, verrouillage,
 * reglages de session et reconnaissance des erreurs. Le code commun n'emet que du SQL
 * portable et passe par ce contrat pour tout le reste.
 */
public interface SqlDialect {

    /**
     * Verrou de migration obtenu ; sa fermeture le libere.
     */
    @FunctionalInterface
    interface MigrationLock extends AutoCloseable {

        /** Verrou sans effet, pour un moteur qui serialise deja les migrations. */
        MigrationLock NONE = () -> { };

        /**
         * Libere le verrou.
         *
         * @throws SQLException erreur du pilote
         */
        @Override
        void close() throws SQLException;
    }

    /**
     * @return nom court du moteur, qui est aussi le dossier de ses scripts de migration
     *         ({@code schema/<nom>/V<n>.sql})
     */
    String name();

    /**
     * @return proprietes transmises au pilote a chaque ouverture de connexion
     */
    Properties connectionProperties();

    /**
     * @return instructions executees sur chaque nouvelle connexion physique, dans l'ordre
     */
    List<String> connectionInitStatements();

    /**
     * Indique si le moteur n'accepte qu'un ecrivain a la fois : les transactions
     * d'ecriture sont alors serialisees dans le processus, avant de prendre une connexion.
     *
     * @return {@code true} si les ecritures doivent etre serialisees
     */
    boolean singleWriter();

    /**
     * @return options ajoutees a la fin d'un {@code CREATE TABLE} cree par le code (et non par
     *         un script de migration), vide si le moteur n'en a pas
     */
    String tableOptions();

    /**
     * Prend le verrou qui empeche deux processus (deux serveurs Minecraft sur la meme base) de
     * migrer le schema en meme temps. Il est tenu par la connexion recue, qui sert a toute la
     * migration.
     *
     * @param connection connexion en autocommit, gardee jusqu'a la liberation
     * @param name       nom du verrou, propre au prefixe des tables
     * @return le verrou, a fermer en fin de migration
     * @throws SQLException si le verrou n'est pas obtenu dans le delai du dialecte
     */
    MigrationLock lockMigration(Connection connection, String name) throws SQLException;

    /**
     * @return suffixe a ajouter a une lecture faite dans une transaction d'ecriture pour
     *         verrouiller les lignes lues (vide si le moteur verrouille deja toute la base)
     */
    String lockRowsSuffix();

    /**
     * Insertion ignoree si la cle existe deja.
     *
     * @param table   table cible
     * @param columns colonnes inserees, dans l'ordre des parametres
     * @return l'instruction parametree
     */
    String insertIgnoringDuplicate(String table, List<String> columns);

    /**
     * Insertion, ou mise a jour des colonnes hors cle si la cle existe deja.
     *
     * @param table      table cible
     * @param columns    colonnes inserees, dans l'ordre des parametres
     * @param keyColumns colonnes de la cle primaire (incluses dans {@code columns})
     * @return l'instruction parametree
     */
    String upsert(String table, List<String> columns, List<String> keyColumns);

    /**
     * Lie un montant exact (jamais de virgule flottante).
     *
     * @param statement instruction
     * @param index     indice du parametre
     * @param amount    montant a l'echelle monetaire, ou {@code null}
     * @throws SQLException erreur du pilote
     */
    void bindMoney(PreparedStatement statement, int index, BigDecimal amount) throws SQLException;

    /**
     * Relit un montant exact.
     *
     * @param resultSet ligne courante
     * @param column    colonne
     * @return le montant a l'echelle monetaire, ou {@code null} si la colonne est nulle
     * @throws SQLException erreur du pilote
     */
    BigDecimal readMoney(ResultSet resultSet, String column) throws SQLException;

    /**
     * @param failure erreur du pilote
     * @return {@code true} si elle signale la violation d'une cle primaire ou d'une
     *         contrainte d'unicite
     */
    boolean isUniqueViolation(SQLException failure);

    /**
     * @param failure erreur du pilote
     * @return {@code true} si la transaction a ete annulee par le moteur pour un conflit
     *         passager (interblocage, attente de verrou depassee) : elle peut etre rejouee
     *         depuis le debut sans rien avoir ecrit
     */
    boolean isRetryable(SQLException failure);

    /**
     * Reconnait, pendant la migration d'une version interrompue a mi-chemin (moteur sans DDL
     * transactionnel), une instruction deja appliquee lors de la tentative precedente.
     *
     * @param failure erreur du pilote sur une instruction de script de migration
     * @return {@code true} si l'objet cree par l'instruction existe deja (colonne, index...)
     */
    boolean isAlreadyApplied(SQLException failure);

    /**
     * Insertion simple, portable sur tous les moteurs.
     *
     * @param table   table cible
     * @param columns colonnes inserees, dans l'ordre des parametres
     * @return l'instruction parametree
     */
    static String insert(String table, List<String> columns) {
        return "INSERT INTO " + table + " (" + String.join(", ", columns) + ") VALUES ("
                + String.join(", ", Collections.nCopies(columns.size(), "?")) + ")";
    }
}
