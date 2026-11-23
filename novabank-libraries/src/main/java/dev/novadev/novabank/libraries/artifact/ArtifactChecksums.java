package dev.novadev.novabank.libraries.artifact;

import dev.novadev.novabank.core.text.ConsoleText;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Calcul et comparaison des sommes SHA-256 des artefacts.
 */
public final class ArtifactChecksums {

    private static final int BUFFER_SIZE = 64 * 1024;

    private ArtifactChecksums() {
    }

    /**
     * @return un nouveau calculateur SHA-256
     */
    public static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            // SHA-256 fait partie des algorithmes obligatoires de toute plateforme Java.
            throw new IllegalStateException(ConsoleText.tr("SHA-256 indisponible"), impossible);
        }
    }

    /**
     * Compare une somme calculee a celle epinglee, en temps constant.
     *
     * @param artifact artefact epingle
     * @param digest   somme calculee
     * @return {@code true} si elles sont egales
     */
    public static boolean matches(LibraryArtifact artifact, byte[] digest) {
        return MessageDigest.isEqual(artifact.expectedDigest(), digest);
    }

    /**
     * Indique si un fichier est exactement l'artefact epingle (fichier regulier, taille et
     * somme SHA-256 identiques).
     *
     * @param artifact artefact epingle
     * @param file     fichier a controler
     * @return {@code true} si le fichier est intact
     * @throws IOException si le fichier ne peut pas etre lu
     */
    public static boolean isIntact(LibraryArtifact artifact, Path file) throws IOException {
        if (!Files.isRegularFile(file) || Files.size(file) != artifact.size()) {
            return false;
        }
        return matches(artifact, sha256(file));
    }

    /**
     * @param file fichier lisible
     * @return la somme SHA-256 de son contenu
     * @throws IOException si la lecture echoue
     */
    public static byte[] sha256(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return sha256(in);
        }
    }

    /**
     * Lit un flux jusqu'au bout (sans le fermer) en calculant sa somme.
     *
     * @param in flux a lire
     * @return la somme SHA-256 du contenu lu
     * @throws IOException si la lecture echoue
     */
    public static byte[] sha256(InputStream in) throws IOException {
        MessageDigest digest = newDigest();
        byte[] buffer = new byte[BUFFER_SIZE];
        int read;
        while ((read = in.read(buffer)) != -1) {
            digest.update(buffer, 0, read);
        }
        return digest.digest();
    }

    /**
     * @param digest somme en octets
     * @return la somme en hexadecimal minuscule
     */
    public static String hex(byte[] digest) {
        return HexFormat.of().formatHex(digest);
    }
}
