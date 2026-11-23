package dev.novadev.novabank.libraries.artifact;

import dev.novadev.novabank.core.text.ConsoleText;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Artefact Maven epingle : coordonnees, somme SHA-256 et taille exactes du jar.
 *
 * <p>Les artefacts ne sont construits que par le code (catalogue compile), jamais depuis
 * la configuration. Chaque segment est valide strictement (lettres, chiffres, {@code .},
 * {@code _}, {@code -}, debut alphanumerique) : le nom de fichier et le chemin dans le
 * depot qui en derivent ne peuvent contenir ni separateur ni {@code ..}, le fichier final
 * reste donc toujours directement dans le dossier des bibliotheques.
 *
 * @param groupId    groupe Maven, segments separes par {@code .}
 * @param artifactId identifiant Maven
 * @param version    version exacte
 * @param sha256     somme SHA-256 du jar, 64 caracteres hexadecimaux minuscules
 * @param size       taille exacte du jar en octets
 */
public record LibraryArtifact(String groupId, String artifactId, String version, String sha256, long size) {

    /** Taille maximale acceptee pour un artefact : 128 Mio. */
    public static final long MAX_SIZE = 128L * 1024 * 1024;

    private static final Pattern SEGMENT = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");
    private static final Pattern GROUP_PART = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,63}");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    /**
     * Valide l'artefact.
     *
     * @throws NullPointerException     si un champ est nul
     * @throws IllegalArgumentException si un champ est mal forme ou la taille hors bornes
     */
    public LibraryArtifact {
        Objects.requireNonNull(groupId, "groupId");
        Objects.requireNonNull(artifactId, "artifactId");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(sha256, "sha256");
        for (String part : groupId.split("\\.", -1)) {
            require(GROUP_PART, part, "groupId", groupId);
        }
        require(SEGMENT, artifactId, "artifactId", artifactId);
        require(SEGMENT, version, "version", version);
        require(SHA256, sha256, "sha256", sha256);
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException(ConsoleText.tr("Taille d'artefact hors bornes : {0}", size));
        }
    }

    /**
     * @return {@code groupId:artifactId:version}
     */
    public String coordinates() {
        return groupId + ':' + artifactId + ':' + version;
    }

    /**
     * @return nom du jar, {@code artifactId-version.jar}
     */
    public String fileName() {
        return artifactId + '-' + version + ".jar";
    }

    /**
     * @return chemin relatif du jar dans un depot Maven (disposition standard)
     */
    public String repositoryPath() {
        return groupId.replace('.', '/') + '/' + artifactId + '/' + version + '/' + fileName();
    }

    /**
     * @return la somme SHA-256 attendue, en octets (copie)
     */
    public byte[] expectedDigest() {
        return HexFormat.of().parseHex(sha256);
    }

    private static void require(Pattern pattern, String value, String field, String whole) {
        if (!pattern.matcher(value).matches()) {
            throw new IllegalArgumentException(ConsoleText.tr("Champ {0} invalide : '{1}'", field, whole));
        }
    }
}
