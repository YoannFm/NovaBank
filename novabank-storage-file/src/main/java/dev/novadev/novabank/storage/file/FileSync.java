package dev.novadev.novabank.storage.file;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Synchronisation d'un dossier sur le disque : rend durable la creation ou le renommage d'un
 * fichier (entree du dossier), en plus du contenu deja synchronise par le fichier lui-meme.
 */
final class FileSync {

    private FileSync() {
    }

    /**
     * Synchronise l'entree du dossier. Sans effet la ou un dossier ne peut pas etre ouvert
     * (Windows : creation et renommage y sont deja durables une fois termines).
     *
     * @param directory dossier a synchroniser
     */
    static void directory(Path directory) {
        try (FileChannel channel = FileChannel.open(directory.toAbsolutePath(), StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException unsupported) {
            // Systeme sans synchronisation de dossier.
        }
    }
}
