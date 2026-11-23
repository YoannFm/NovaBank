package dev.novadev.novabank.storage.file;

import dev.novadev.novabank.core.repository.StorageException;
import dev.novadev.novabank.core.text.ConsoleText;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Verrou de fichier exclusif ({@link FileLock}) sur les donnees d'un stockage fichier : un second
 * processus (second serveur pointant sur le meme dossier) ou une seconde ouverture dans le meme
 * processus est refusee. Le fichier de verrou contient le numero du processus detenteur ; il
 * n'est jamais supprime (seul le verrou du systeme fait foi, libere meme apres un arret brutal).
 */
final class DataLock implements AutoCloseable {

    private final FileChannel channel;
    private final FileLock lock;

    private DataLock(FileChannel channel, FileLock lock) {
        this.channel = channel;
        this.lock = lock;
    }

    /**
     * @param file fichier de verrou
     * @return le verrou pris
     * @throws StorageException si un autre processus (ou ce processus) detient deja le verrou, ou
     *                          si le fichier est inaccessible
     */
    static DataLock acquire(Path file) {
        FileChannel channel;
        try {
            channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.READ,
                    StandardOpenOption.WRITE);
        } catch (IOException failure) {
            throw new StorageException(ConsoleText.tr("Fichier de verrou {0} inaccessible : {1}",
                    file, failure.getMessage()), failure);
        }
        try {
            FileLock lock;
            try {
                lock = channel.tryLock();
            } catch (OverlappingFileLockException sameProcess) {
                lock = null;
            }
            if (lock == null) {
                String holder = holder(file);
                channel.close();
                throw new StorageException(
                        ConsoleText.tr("Donnees {0} deja utilisees par un autre serveur{1} (verrou {2}) : un stockage YAML/JSON ne sert qu'un seul serveur a la fois (utilisez MySQL, MariaDB ou MongoDB pour plusieurs serveurs)",
                                file.getParent(), holder, file.getFileName()));
            }
            byte[] pid = (ProcessHandle.current().pid() + "\n").getBytes(StandardCharsets.UTF_8);
            channel.truncate(0);
            channel.write(ByteBuffer.wrap(pid), 0);
            return new DataLock(channel, lock);
        } catch (IOException failure) {
            closeQuietly(channel, failure);
            throw new StorageException(ConsoleText.tr("Verrou {0} impossible : {1}",
                    file, failure.getMessage()), failure);
        }
    }

    @Override
    public void close() throws IOException {
        try {
            lock.release();
        } finally {
            channel.close();
        }
    }

    private static String holder(Path file) {
        try {
            String pid = Files.readString(file, StandardCharsets.UTF_8).trim();
            return pid.isEmpty() ? "" : " (processus " + pid + ")";
        } catch (IOException unreadable) {
            return "";
        }
    }

    private static void closeQuietly(FileChannel channel, IOException failure) {
        try {
            channel.close();
        } catch (IOException closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }
}
