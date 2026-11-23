package dev.novadev.novabank.libraries.testing;

import dev.novadev.novabank.libraries.artifact.ArtifactChecksums;
import dev.novadev.novabank.libraries.artifact.LibraryArtifact;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/**
 * Faux artefacts : contenu arbitraire et artefact epingle correspondant.
 */
public final class TestArtifacts {

    private TestArtifacts() {
    }

    /**
     * @param size taille
     * @param seed graine (contenus reproductibles et distincts)
     * @return un contenu pseudo-aleatoire
     */
    public static byte[] content(int size, long seed) {
        byte[] bytes = new byte[size];
        new Random(seed).nextBytes(bytes);
        return bytes;
    }

    /**
     * @param artifactId identifiant de l'artefact
     * @param content    contenu exact du jar
     * @return l'artefact {@code dev.example:artifactId:1.0} epingle sur ce contenu
     */
    public static LibraryArtifact pinned(String artifactId, byte[] content) {
        return new LibraryArtifact("dev.example", artifactId, "1.0",
                ArtifactChecksums.hex(ArtifactChecksums.newDigest().digest(content)), content.length);
    }

    /**
     * @param jar jar existant
     * @return l'artefact {@code dev.example:artifactId:1.0} epingle sur ce fichier
     */
    public static LibraryArtifact pinned(String artifactId, Path jar) {
        try {
            return pinned(artifactId, Files.readAllBytes(jar));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
