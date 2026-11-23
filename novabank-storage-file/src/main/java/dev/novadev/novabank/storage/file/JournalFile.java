package dev.novadev.novabank.storage.file;

import dev.novadev.novabank.core.text.ConsoleText;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;

/**
 * Journal d'ecriture append-only : une ligne UTF-8 par mutation validee.
 *
 * <h2>Format d'une ligne</h2>
 * <pre>&lt;sequence&gt; &lt;crc32&gt; &lt;contenu&gt;\n</pre>
 * <ul>
 *   <li>{@code sequence} : entier decimal, +1 a chaque ligne (continue la sequence de
 *       l'instantane) ;</li>
 *   <li>{@code crc32} : 8 chiffres hexadecimaux, CRC32 des octets UTF-8 de
 *       {@code "<sequence> <contenu>"} ;</li>
 *   <li>{@code contenu} : le {@link Commit} en forme compacte ({@link CompactTree}, sous-ensemble
 *       de JSON sur une ligne, memes champs que l'instantane, voir {@link TreeMapping}).</li>
 * </ul>
 *
 * <p>Chaque ligne est ecrite en un seul appel puis synchronisee ({@code force(true)} : contenu et
 * metadonnees, dont la taille) avant que {@link #append} rende la main. Une ligne invalide en
 * derniere position (ecriture interrompue par un arret brutal, donc jamais confirmee) est retiree a
 * l'ouverture et conservee dans un fichier {@code .torn-<date>} ; une ligne invalide suivie
 * d'autres lignes est une corruption : ouverture refusee.
 */
final class JournalFile implements AutoCloseable {

    private final Path path;
    private final FileChannel channel;
    private long size;

    private JournalFile(Path path, FileChannel channel, long size) {
        this.path = path;
        this.channel = channel;
        this.size = size;
    }

    /**
     * Ligne relue.
     *
     * @param sequence numero de la ligne
     * @param commit   elements enregistres
     */
    record Entry(long sequence, Commit commit) {
    }

    /**
     * Journal ouvert et lignes valides relues.
     *
     * @param journal     journal pret pour les ajouts
     * @param entries     lignes valides, dans l'ordre
     * @param tornBytes   octets d'une derniere ligne tronquee retiree (0 si aucune)
     * @param tornCopy    fichier qui conserve ces octets, {@code null} si aucun
     */
    record Opened(JournalFile journal, List<Entry> entries, long tornBytes, Path tornCopy) {
    }

    /**
     * Ouvre (ou cree) le journal et relit ses lignes.
     *
     * @param path       fichier du journal
     * @param tornSuffix suffixe du fichier de conservation d'une ligne tronquee
     * @return le journal et ses lignes
     * @throws IOException           erreur d'acces au fichier
     * @throws CorruptDataException  si une ligne invalide est suivie d'autres lignes, ou si la
     *                               sequence n'est pas continue
     */
    static Opened open(Path path, String tornSuffix) throws IOException {
        boolean created = !Files.exists(path);
        FileChannel channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.READ,
                StandardOpenOption.WRITE);
        try {
            if (created) {
                // Journal neuf : son entree dans le dossier doit survivre a une coupure, sinon les
                // premieres lignes synchronisees seraient perdues avec le fichier.
                channel.force(true);
                FileSync.directory(path.toAbsolutePath().getParent());
            }
            byte[] content = Files.readAllBytes(path);
            List<Entry> entries = new ArrayList<>();
            int start = 0;
            int lineNumber = 0;
            long validLength = 0;
            while (start < content.length) {
                lineNumber++;
                int end = indexOf(content, (byte) '\n', start);
                boolean terminated = end >= 0;
                int stop = terminated ? end : content.length;
                Entry entry = terminated ? parse(new String(content, start, stop - start, StandardCharsets.UTF_8),
                        path.getFileName() + " ligne " + lineNumber) : null;
                boolean last = !terminated || stop + 1 >= content.length;
                if (entry == null) {
                    if (!last) {
                        throw new CorruptDataException(
                                ConsoleText.tr("{0} : ligne {1} invalide suivie d'autres lignes (fichier corrompu)",
                                        path.getFileName(), lineNumber));
                    }
                    break;
                }
                if (!entries.isEmpty() && entry.sequence() != entries.get(entries.size() - 1).sequence() + 1) {
                    throw new CorruptDataException(ConsoleText.tr("{0} : ligne {1} : sequence {2} non continue",
                            path.getFileName(), lineNumber, entry.sequence()));
                }
                entries.add(entry);
                validLength = stop + 1;
                start = stop + 1;
            }
            long tornBytes = content.length - validLength;
            Path tornCopy = null;
            if (tornBytes > 0) {
                tornCopy = path.resolveSibling(path.getFileName() + tornSuffix);
                Files.write(tornCopy, Arrays.copyOfRange(content, (int) validLength, content.length),
                        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                channel.truncate(validLength);
                channel.force(true);
            }
            return new Opened(new JournalFile(path, channel, validLength), entries, tornBytes, tornCopy);
        } catch (IOException | RuntimeException failure) {
            channel.close();
            throw failure;
        }
    }

    /**
     * Ajoute une ligne et la synchronise sur le disque.
     *
     * @param sequence numero de la ligne
     * @param commit   elements
     * @throws IOException si l'ecriture ou la synchronisation echoue (contenu du fichier alors
     *                     indetermine pour cette ligne)
     */
    void append(long sequence, Commit commit) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(line(sequence, commit).getBytes(StandardCharsets.UTF_8));
        long position = size;
        while (buffer.hasRemaining()) {
            position += channel.write(buffer, position);
        }
        channel.force(true);
        size = position;
    }

    /**
     * Vide le journal (apres un instantane qui contient toutes ses lignes).
     *
     * @throws IOException erreur d'ecriture
     */
    void reset() throws IOException {
        channel.truncate(0);
        // Position d'ecriture recalee des la troncature : meme si la synchronisation echoue, les
        // lignes suivantes ne laissent pas de trou dans le fichier.
        size = 0;
        channel.force(true);
    }

    /**
     * @return taille du journal en octets
     */
    long size() {
        return size;
    }

    /**
     * @return fichier du journal
     */
    Path path() {
        return path;
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }

    /**
     * @param sequence numero de la ligne
     * @param commit   elements
     * @return la ligne complete, saut de ligne compris
     */
    static String line(long sequence, Commit commit) {
        Map<String, Object> tree = new LinkedHashMap<>();
        TreeMapping.write(commit, tree);
        String payload = CompactTree.write(tree);
        return sequence + " " + crc(sequence + " " + payload) + " " + payload + "\n";
    }

    /**
     * @param line  ligne sans son saut de ligne
     * @param where fichier et numero de ligne, pour les messages
     * @return la ligne lue, ou {@code null} si elle est mal formee ou si son CRC est faux (ecriture
     *         interrompue)
     * @throws CorruptDataException si le CRC est juste mais le contenu invalide (jamais du a une
     *                              ecriture interrompue)
     */
    private static Entry parse(String line, String where) {
        int firstSpace = line.indexOf(' ');
        int secondSpace = firstSpace < 0 ? -1 : line.indexOf(' ', firstSpace + 1);
        if (secondSpace < 0) {
            return null;
        }
        String sequenceText = line.substring(0, firstSpace);
        String payload = line.substring(secondSpace + 1);
        if (!line.substring(firstSpace + 1, secondSpace).equals(crc(sequenceText + " " + payload))) {
            return null;
        }
        long sequence;
        try {
            sequence = Long.parseLong(sequenceText);
        } catch (NumberFormatException invalid) {
            throw new CorruptDataException(ConsoleText.tr("{0} : sequence invalide", where), invalid);
        }
        try {
            return new Entry(sequence, TreeMapping.read(new TreeReader(CompactTree.read(payload), where)));
        } catch (CorruptDataException invalid) {
            throw new CorruptDataException(ConsoleText.tr("{0} : {1}", where, invalid.getMessage()), invalid);
        }
    }

    private static String crc(String text) {
        CRC32 crc = new CRC32();
        crc.update(text.getBytes(StandardCharsets.UTF_8));
        return String.format("%08x", crc.getValue());
    }

    private static int indexOf(byte[] content, byte value, int from) {
        for (int index = from; index < content.length; index++) {
            if (content[index] == value) {
                return index;
            }
        }
        return -1;
    }
}
