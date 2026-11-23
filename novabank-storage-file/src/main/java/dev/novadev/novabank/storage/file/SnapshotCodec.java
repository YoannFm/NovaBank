package dev.novadev.novabank.storage.file;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Map;

/**
 * Format d'un instantane : seule partie du stockage fichier qui differe entre YAML et JSON.
 *
 * <p>L'arbre ecrit et relu ne contient que des {@link Map} (cles texte, ordre conserve), des
 * {@link java.util.List} et des {@link String} : nombres et montants sont des textes exacts.
 */
interface SnapshotCodec {

    /**
     * @return extension du fichier, sans point ({@code json}, {@code yml})
     */
    String extension();

    /**
     * @param tree   arbre a ecrire
     * @param output destination (non fermee)
     * @throws IOException erreur d'ecriture
     */
    void write(Map<String, Object> tree, Writer output) throws IOException;

    /**
     * @param input source (non fermee)
     * @return l'arbre lu
     * @throws IOException erreur de lecture ou de syntaxe
     */
    Map<String, Object> read(Reader input) throws IOException;
}
