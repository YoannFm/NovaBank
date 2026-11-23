package dev.novadev.novabank.storage.sql;

import dev.novadev.novabank.core.repository.StorageException;
import dev.novadev.novabank.core.text.ConsoleText;
import dev.novadev.novabank.storage.sql.dialect.SqlDialect;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import javax.sql.DataSource;

/**
 * Execute les lectures et les transactions d'ecriture, et traduit les erreurs du pilote.
 *
 * <p>Une ecriture est entierement validee ou entierement annulee : toute exception
 * (erreur SQL, mutation qui echoue, conflit) provoque un rollback avant d'etre propagee.
 * Les erreurs SQL deviennent des {@link StorageException} qui gardent leur cause ; les
 * autres exceptions sont propagees telles quelles. Les connexions sont rendues au pool
 * en autocommit : sous SQLite en mode {@code IMMEDIATE}, une connexion laissee hors
 * autocommit garderait le verrou d'ecriture.
 *
 * <p>Conflits : un {@link WriteConflict} leve par le travail, ou une erreur que le dialecte
 * declare rejouable (interblocage, attente de verrou depassee sous MySQL / MariaDB), annule la
 * transaction puis la rejoue depuis le debut sur une nouvelle connexion, au plus
 * {@link #MAX_ATTEMPTS} fois, apres une courte attente aleatoire croissante. Un thread
 * interrompu pendant cette attente cesse de rejouer : le conflit est alors traite comme
 * persistant (rien n'a ete ecrit).
 */
final class SqlTransactions {

    /** Nombre maximal de tentatives d'une transaction en conflit. */
    static final int MAX_ATTEMPTS = 5;

    private static final long BACKOFF_STEP_MS = 20L;

    private final DataSource dataSource;
    private final SqlDialect dialect;
    private final ReentrantLock writeLock;

    /**
     * @param dataSource source des connexions
     * @param dialect    dialecte : serialisation des ecritures et erreurs rejouables
     */
    SqlTransactions(DataSource dataSource, SqlDialect dialect) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.dialect = Objects.requireNonNull(dialect, "dialect");
        this.writeLock = dialect.singleWriter() ? new ReentrantLock(true) : null;
    }

    /**
     * Lecture en autocommit : une seule instruction, donc un seul instantane coherent.
     *
     * @param action description pour les messages d'erreur
     * @param work   travail
     * @param <T>    type du resultat
     * @return le resultat
     * @throws StorageException sur erreur SQL
     */
    <T> T read(String action, SqlWork<T> work) {
        try (Connection connection = dataSource.getConnection()) {
            return work.run(connection);
        } catch (SQLException failure) {
            throw storageFailure(action, failure);
        }
    }

    /**
     * Transaction d'ecriture, rejouee en cas de conflit.
     *
     * @param action description pour les messages d'erreur
     * @param work   travail ; il est valide s'il se termine normalement
     * @param <T>    type du resultat
     * @return le resultat
     * @throws StorageException sur erreur SQL ou conflit persistant, apres rollback
     */
    <T> T write(String action, SqlWork<T> work) {
        return writeOrConflict(action, work, () -> {
            throw new StorageException(ConsoleText.tr("Conflit d'ecriture persistant ({0}), rien n'a ete ecrit",
                    action));
        });
    }

    /**
     * Transaction d'ecriture, rejouee en cas de conflit ; si le conflit persiste, rien n'est
     * ecrit et le resultat de repli est rendu.
     *
     * @param action     description pour les messages d'erreur
     * @param work       travail ; il est valide s'il se termine normalement
     * @param onConflict resultat si toutes les tentatives sont en conflit
     * @param <T>        type du resultat
     * @return le resultat du travail, ou celui de {@code onConflict}
     * @throws StorageException sur erreur SQL non rejouable, apres rollback
     */
    <T> T writeOrConflict(String action, SqlWork<T> work, Supplier<T> onConflict) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return writeOnce(action, work);
            } catch (WriteConflict conflict) {
                if (attempt < MAX_ATTEMPTS && !backOff(attempt)) {
                    break;
                }
            }
        }
        return onConflict.get();
    }

    /**
     * Garde une seule connexion, en autocommit, pour un travail fait de plusieurs transactions
     * (migration : le verrou de migration MySQL est lie a la session). Sous un moteur a
     * ecrivain unique, les autres ecritures du processus attendent la fin du travail.
     *
     * @param action description pour les messages d'erreur
     * @param work   travail ; il delimite ses transactions avec {@link #inTransaction}
     * @param <T>    type du resultat
     * @return le resultat
     * @throws StorageException sur erreur SQL
     */
    <T> T session(String action, SqlWork<T> work) {
        lockWriter();
        try (Connection connection = dataSource.getConnection()) {
            return work.run(connection);
        } catch (SQLException failure) {
            throw storageFailure(action, failure);
        } finally {
            unlockWriter();
        }
    }

    /**
     * Une transaction sur une connexion de {@link #session}, sans rejeu.
     *
     * @param connection connexion en autocommit
     * @param work       travail ; il est valide s'il se termine normalement
     * @param <T>        type du resultat
     * @return le resultat
     * @throws SQLException erreur du pilote, apres rollback
     */
    static <T> T inTransaction(Connection connection, SqlWork<T> work) throws SQLException {
        connection.setAutoCommit(false);
        T result;
        try {
            result = work.run(connection);
            connection.commit();
        } catch (SQLException | RuntimeException | Error failure) {
            rollback(connection, failure);
            throw failure;
        }
        connection.setAutoCommit(true);
        return result;
    }

    private <T> T writeOnce(String action, SqlWork<T> work) {
        lockWriter();
        try (Connection connection = dataSource.getConnection()) {
            return inTransaction(connection, work);
        } catch (SQLException failure) {
            if (dialect.isRetryable(failure)) {
                throw new WriteConflict("Transaction annulee par le moteur (code " + failure.getErrorCode() + ")");
            }
            throw storageFailure(action, failure);
        } finally {
            unlockWriter();
        }
    }

    private void lockWriter() {
        if (writeLock != null) {
            writeLock.lock();
        }
    }

    private void unlockWriter() {
        if (writeLock != null) {
            writeLock.unlock();
        }
    }

    /**
     * @return {@code false} si le thread a ete interrompu (drapeau d'interruption retabli)
     */
    private static boolean backOff(int attempt) {
        try {
            TimeUnit.MILLISECONDS.sleep(ThreadLocalRandom.current().nextLong(1L, BACKOFF_STEP_MS * attempt + 1));
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static void rollback(Connection connection, Throwable failure) {
        try {
            connection.rollback();
            connection.setAutoCommit(true);
        } catch (SQLException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }

    private static StorageException storageFailure(String action, SQLException failure) {
        return new StorageException(ConsoleText.tr("Erreur de base de donnees ({0}) : {1}",
                action, failure.getMessage()), failure);
    }
}
