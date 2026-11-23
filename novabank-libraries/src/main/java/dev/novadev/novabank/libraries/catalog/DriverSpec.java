package dev.novadev.novabank.libraries.catalog;

import dev.novadev.novabank.core.text.ConsoleText;
import dev.novadev.novabank.libraries.artifact.LibraryArtifact;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Pilote JDBC a charger : classe du pilote et artefacts necessaires.
 *
 * @param id                      identifiant court ({@code sqlite}, {@code mariadb}), sert au nom du chargeur
 * @param driverClassName         nom de la classe implementant {@link java.sql.Driver}
 * @param artifacts               jar du pilote puis ses dependances d'execution, listees explicitement
 *                                (aucune resolution Maven dynamique)
 * @param nativeDirectoryProperty propriete systeme fixant le dossier d'extraction de la
 *                                bibliotheque native du pilote, s'il en a une
 */
public record DriverSpec(String id, String driverClassName, List<LibraryArtifact> artifacts,
                         Optional<String> nativeDirectoryProperty) {

    /**
     * Valide la description et copie la liste des artefacts.
     *
     * @throws NullPointerException     si un champ est nul
     * @throws IllegalArgumentException si aucun artefact n'est liste
     */
    public DriverSpec {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(driverClassName, "driverClassName");
        Objects.requireNonNull(nativeDirectoryProperty, "nativeDirectoryProperty");
        artifacts = List.copyOf(artifacts);
        if (artifacts.isEmpty()) {
            throw new IllegalArgumentException(ConsoleText.tr("Aucun artefact pour le pilote {0}", id));
        }
    }
}
