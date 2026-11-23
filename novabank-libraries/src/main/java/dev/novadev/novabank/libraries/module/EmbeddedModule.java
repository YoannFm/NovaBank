package dev.novadev.novabank.libraries.module;

import dev.novadev.novabank.core.text.ConsoleText;
import dev.novadev.novabank.libraries.artifact.LibraryArtifact;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Description d'un module embarque.
 *
 * <p>Le jar du module est la ressource {@code resource} du jar du plugin ; sa somme SHA-256 est
 * la ressource {@code resource + ".sha256"}, generee au build. Il est extrait dans le dossier des
 * bibliotheques au meme chemin relatif ({@code libs/backends/mongodb.jar} pour
 * {@code backends/mongodb.jar}).
 *
 * @param id             identifiant court (nom du chargeur, journaux)
 * @param resource       chemin de la ressource du jar du module, relatif, segments {@code [a-z0-9._-]}
 * @param entryClassName classe publique du module instanciee par son constructeur sans argument
 * @param libraries      pilotes epingles du module, telecharges et verifies (D6)
 */
public record EmbeddedModule(String id, String resource, String entryClassName, List<LibraryArtifact> libraries) {

    /** Suffixe de la ressource qui porte la somme SHA-256 du jar du module. */
    public static final String CHECKSUM_SUFFIX = ".sha256";

    private static final Pattern RESOURCE = Pattern.compile("[a-z0-9_-]+(/[a-z0-9_-]+)*\\.jar");

    /**
     * Valide la description et copie la liste des pilotes.
     *
     * @throws IllegalArgumentException si le chemin de la ressource est invalide
     */
    public EmbeddedModule {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(entryClassName, "entryClassName");
        libraries = List.copyOf(libraries);
        if (!RESOURCE.matcher(resource).matches()) {
            throw new IllegalArgumentException(ConsoleText.tr("Ressource de module invalide : {0}", resource));
        }
    }

    /**
     * @return le chemin de la ressource portant la somme SHA-256 du jar du module
     */
    public String checksumResource() {
        return resource + CHECKSUM_SUFFIX;
    }
}
