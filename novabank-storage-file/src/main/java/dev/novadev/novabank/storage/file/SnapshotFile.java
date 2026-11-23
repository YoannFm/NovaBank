package dev.novadev.novabank.storage.file;

import dev.novadev.novabank.core.text.ConsoleText;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Instantane du stockage, au format choisi ({@link SnapshotCodec}) :
 *
 * <pre>
 * format: novabank-file
 * format-version: "1"
 * sequence: "&lt;derniere ligne du journal incluse&gt;"
 * banks / operations / deliveries / active-banks : voir {@link TreeMapping}
 * </pre>
 *
 * <p>Ecriture atomique : fichier temporaire ecrit puis synchronise ({@code force(true)}), renomme
 * par-dessus l'instantane ({@code ATOMIC_MOVE}), puis dossier synchronise (quand le systeme le
 * permet) : apres un arret brutal, on trouve l'ancien instantane ou le nouveau, jamais un melange.
 */
final class SnapshotFile {

    static final String FORMAT = "novabank-file";
    static final String FORMAT_VERSION = "1";

    private final Path file;
    private final Path temporary;
    private final SnapshotCodec codec;

    SnapshotFile(Path file, SnapshotCodec codec) {
        this.file = file;
        this.temporary = file.resolveSibling(file.getFileName() + ".tmp");
        this.codec = codec;
    }

    /**
     * Contenu relu.
     *
     * @param sequence derniere ligne du journal incluse
     * @param content  etat complet
     */
    record Snapshot(long sequence, Commit content) {
    }

    /**
     * @return l'instantane, ou vide s'il n'existe pas encore
     * @throws IOException          erreur de lecture ou de syntaxe
     * @throws CorruptDataException si le contenu est invalide
     */
    Optional<Snapshot> read() throws IOException {
        Map<String, Object> tree;
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            tree = codec.read(reader);
        } catch (NoSuchFileException absent) {
            return Optional.empty();
        }
        TreeReader root = TreeReader.root(tree, file.getFileName().toString());
        if (!FORMAT.equals(root.text("format"))) {
            throw new CorruptDataException(ConsoleText.tr("{0} : format '{1}' inconnu (attendu : {2})",
                    file.getFileName(), root.text("format"), FORMAT));
        }
        if (!FORMAT_VERSION.equals(root.text("format-version"))) {
            throw new CorruptDataException(
                    ConsoleText.tr("{0} : version de format {1} non geree par cette version de NovaBank ({2}) : mettez NovaBank a jour",
                            file.getFileName(), root.text("format-version"), FORMAT_VERSION));
        }
        return Optional.of(new Snapshot(root.longValue("sequence"), TreeMapping.read(root)));
    }

    /**
     * Remplace atomiquement l'instantane.
     *
     * @param sequence derniere ligne du journal incluse
     * @param content  etat complet
     * @throws IOException erreur d'ecriture (l'instantane precedent reste intact)
     */
    void write(long sequence, Commit content) throws IOException {
        Map<String, Object> tree = new LinkedHashMap<>();
        tree.put("format", FORMAT);
        tree.put("format-version", FORMAT_VERSION);
        tree.put("sequence", Long.toString(sequence));
        TreeMapping.write(content, tree);
        try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            Writer writer = new BufferedWriter(new OutputStreamWriter(Channels.newOutputStream(channel),
                    StandardCharsets.UTF_8));
            codec.write(tree, writer);
            writer.flush();
            channel.force(true);
        }
        Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        FileSync.directory(file.toAbsolutePath().getParent());
    }

    /**
     * @return fichier de l'instantane
     */
    Path path() {
        return file;
    }
}
