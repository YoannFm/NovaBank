package dev.novadev.novabank.storage.file;

import dev.novadev.novabank.core.repository.StorageException;
import dev.novadev.novabank.core.text.ConsoleText;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Moteur commun YAML / JSON : etat en memoire sous verrou lecture/ecriture, journal d'ecriture,
 * instantane, verrou de fichier. Seul le {@link SnapshotCodec} differe d'un format a l'autre.
 *
 * <h2>Garanties</h2>
 * <ul>
 *   <li>une mutation n'est appliquee en memoire (et donc confirmee) qu'apres l'ecriture ET la
 *       synchronisation de sa ligne de journal : toute operation confirmee survit a un arret
 *       brutal ou a une coupure de courant ;</li>
 *   <li>une ligne contient tous les elements d'une mutation (banque et entree de journal) :
 *       jamais de mutation a moitie appliquee ;</li>
 *   <li>apres un echec d'ecriture du journal, plus aucune mutation n'est acceptee (le contenu du
 *       disque est alors incertain) : il faut redemarrer, le disque fait foi ;</li>
 *   <li>une compaction en echec ne perd rien : le journal est conserve et la compaction retentee
 *       plus tard.</li>
 * </ul>
 */
final class FileStore implements AutoCloseable {

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final FileState state;
    private final DataLock dataLock;
    private final SnapshotFile snapshot;
    private final JournalFile journal;
    private final FileStorageOptions options;
    private final Logger logger;
    private long sequence;
    private Instant lastCompaction;
    private IOException writeFailure;
    private boolean closed;

    private FileStore(FileState state, DataLock dataLock, SnapshotFile snapshot, JournalFile journal, long sequence,
                      FileStorageOptions options) {
        this.state = state;
        this.dataLock = dataLock;
        this.snapshot = snapshot;
        this.journal = journal;
        this.sequence = sequence;
        this.options = options;
        this.logger = options.logger();
        this.lastCompaction = options.clock().instant();
    }

    /**
     * Ouvre le stockage : verrou, instantane, rejeu du journal, puis compaction si le journal
     * n'etait pas vide.
     *
     * @param options reglages
     * @return le moteur pret
     * @throws StorageException si les donnees sont deja utilisees, illisibles ou corrompues (aucune
     *                          donnee n'est alors modifiee, hormis le retrait d'une derniere ligne
     *                          tronquee du journal, conservee a part)
     */
    static FileStore open(FileStorageOptions options) {
        SnapshotCodec codec = options.format().codec();
        String base = "novabank." + codec.extension();
        Path directory = options.directory();
        try {
            Files.createDirectories(directory);
        } catch (IOException failure) {
            throw new StorageException(ConsoleText.tr("Dossier de donnees {0} non creable : {1}",
                    directory, failure.getMessage()),
                    failure);
        }
        DataLock dataLock = DataLock.acquire(directory.resolve(base + ".lock"));
        JournalFile journal = null;
        try {
            SnapshotFile snapshot = new SnapshotFile(directory.resolve(base), codec);
            FileState state = new FileState();
            Optional<SnapshotFile.Snapshot> saved = snapshot.read();
            long sequence = 0L;
            if (saved.isPresent()) {
                state.apply(saved.get().content());
                sequence = saved.get().sequence();
            }
            JournalFile.Opened opened = JournalFile.open(directory.resolve(base + ".journal"),
                    ".torn-" + options.clock().millis());
            journal = opened.journal();
            int replayed = 0;
            for (JournalFile.Entry entry : opened.entries()) {
                if (entry.sequence() <= sequence) {
                    continue;
                }
                if (entry.sequence() != sequence + 1) {
                    throw new CorruptDataException(ConsoleText.tr("{0} : ligne {1} manquante (lu {2})",
                            journal.path().getFileName(), (sequence + 1), entry.sequence()));
                }
                state.apply(entry.commit());
                sequence = entry.sequence();
                replayed++;
            }
            if (opened.tornBytes() > 0) {
                options.logger().warning(
                        ConsoleText.tr("Stockage {0} : derniere ligne du journal tronquee (arret brutal pendant une ecriture, operation jamais confirmee) ignoree, {1} octet(s) conserve(s) dans {2}",
                                options.format(), opened.tornBytes(), opened.tornCopy().getFileName()));
            }
            FileStore store = new FileStore(state, dataLock, snapshot, journal, sequence, options);
            if (journal.size() > 0) {
                options.logger().info(
                        ConsoleText.tr("Stockage {0} : {1} operation(s) du journal rejouee(s), compaction",
                                options.format(), replayed));
                store.compact();
            }
            return store;
        } catch (IOException | CorruptDataException failure) {
            closeQuietly(journal, dataLock, failure);
            throw new StorageException(ConsoleText.tr("Stockage {0} illisible dans {1} : {2} (aucune donnee modifiee)",
                    options.format(), directory, failure.getMessage()), failure);
        } catch (RuntimeException failure) {
            closeQuietly(journal, dataLock, failure);
            throw failure;
        }
    }

    /**
     * Lecture sous verrou partage.
     *
     * @param query lecture de l'etat
     * @param <T>   type du resultat
     * @return le resultat
     */
    <T> T read(Function<FileState, T> query) {
        lock.readLock().lock();
        try {
            requireOpen();
            return query.apply(state);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Lecture-decision-ecriture sous verrou exclusif ; l'action enregistre ses mutations par
     * {@link #commit(Commit)}.
     *
     * @param action action sur l'etat
     * @param <T>    type du resultat
     * @return le resultat
     */
    <T> T write(Function<FileState, T> action) {
        lock.writeLock().lock();
        try {
            requireOpen();
            return action.apply(state);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Ecrit et synchronise une ligne de journal, puis applique la mutation en memoire. Appele
     * uniquement depuis {@link #write(Function)}.
     *
     * @param commit elements a enregistrer
     * @throws StorageException si l'ecriture echoue (le stockage refuse alors toute mutation)
     */
    void commit(Commit commit) {
        if (!lock.isWriteLockedByCurrentThread()) {
            throw new IllegalStateException(ConsoleText.tr("Mutation hors du verrou exclusif"));
        }
        if (writeFailure != null) {
            throw new StorageException(
                    ConsoleText.tr("Stockage {0} hors service depuis un echec d'ecriture ({1}) : redemarrez le serveur",
                            options.format(), writeFailure.getMessage()), writeFailure);
        }
        long next = sequence + 1;
        try {
            journal.append(next, commit);
        } catch (IOException failure) {
            writeFailure = failure;
            logger.severe(
                    ConsoleText.tr("Stockage {0} : ecriture du journal {1} impossible ({2}) : plus aucune operation acceptee jusqu'au redemarrage",
                            options.format(), journal.path(), failure.getMessage()));
            throw new StorageException(ConsoleText.tr("Ecriture du journal impossible : {0}",
                    failure.getMessage()), failure);
        }
        sequence = next;
        state.apply(commit);
        Instant now = options.clock().instant();
        if (journal.size() >= options.compactionBytes()
                || !now.isBefore(lastCompaction.plus(options.compactionInterval()))) {
            compact();
        }
    }

    /**
     * Ferme le stockage : compaction finale (sauf apres un echec d'ecriture), fermeture du
     * journal, liberation du verrou. Idempotent.
     *
     * @throws StorageException si la fermeture des fichiers echoue
     */
    @Override
    public void close() {
        lock.writeLock().lock();
        try {
            if (closed) {
                return;
            }
            closed = true;
            if (writeFailure == null && journal.size() > 0) {
                compact();
            }
            try {
                journal.close();
            } finally {
                dataLock.close();
            }
        } catch (IOException failure) {
            throw new StorageException(ConsoleText.tr("Fermeture du stockage {0} impossible : {1}",
                    options.format(), failure.getMessage()), failure);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * @return le journal d'ecriture (tests, diagnostic)
     */
    Path journalPath() {
        return journal.path();
    }

    /**
     * @return l'instantane (tests, diagnostic)
     */
    Path snapshotPath() {
        return snapshot.path();
    }

    /**
     * Verifie que le stockage accepte encore des mutations.
     *
     * @throws StorageException apres un echec d'ecriture ou une fermeture
     */
    void requireWritable() {
        read(current -> {
            if (writeFailure != null) {
                throw new StorageException(ConsoleText.tr("Stockage {0} hors service depuis un echec d'ecriture : {1}",
                        options.format(), writeFailure.getMessage()));
            }
            return null;
        });
    }

    /** Instantane puis journal vide ; un echec est journalise, le journal est conserve. */
    private void compact() {
        lastCompaction = options.clock().instant();
        try {
            snapshot.write(sequence, state.snapshot());
        } catch (IOException | RuntimeException failure) {
            logger.warning(
                    ConsoleText.tr("Stockage {0} : instantane {1} non ecrit ({2}), journal conserve, nouvel essai plus tard",
                            options.format(), snapshot.path().getFileName(), failure));
            return;
        }
        try {
            journal.reset();
        } catch (IOException failure) {
            // Lignes deja incluses dans l'instantane : ignorees au rejeu grace a la sequence.
            logger.warning(ConsoleText.tr("Stockage {0} : journal non vide apres l'instantane ({1})",
                    options.format(), failure));
        }
    }

    private void requireOpen() {
        if (closed) {
            throw new StorageException(ConsoleText.tr("Stockage {0} ferme", options.format()));
        }
    }

    private static void closeQuietly(JournalFile journal, DataLock dataLock, Exception failure) {
        try {
            if (journal != null) {
                journal.close();
            }
        } catch (IOException closeFailure) {
            failure.addSuppressed(closeFailure);
        }
        try {
            dataLock.close();
        } catch (IOException closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }
}
