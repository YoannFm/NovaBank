package dev.novadev.novabank.libraries.artifact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.novadev.novabank.libraries.testing.TestArtifacts;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LibraryArtifactTest {

    private static final String SHA = "bcb1f51e36f940867e83342f9efbf5968ac44a6bef4d397bb4af7b17b45cd2fb";

    @Test
    void derive_coordonnees_nom_et_chemin_maven() {
        LibraryArtifact artifact = new LibraryArtifact("org.xerial", "sqlite-jdbc", "3.53.4.0", SHA, 10);

        assertThat(artifact.coordinates()).isEqualTo("org.xerial:sqlite-jdbc:3.53.4.0");
        assertThat(artifact.fileName()).isEqualTo("sqlite-jdbc-3.53.4.0.jar");
        assertThat(artifact.repositoryPath()).isEqualTo("org/xerial/sqlite-jdbc/3.53.4.0/sqlite-jdbc-3.53.4.0.jar");
        assertThat(ArtifactChecksums.hex(artifact.expectedDigest())).isEqualTo(SHA);
    }

    @ParameterizedTest
    @ValueSource(strings = {"..", "../evil", "a/b", "a\\b", ".hidden", "", "a b", "x%2f"})
    void refuse_un_artifactId_ou_une_version_manipulable(String value) {
        assertThatThrownBy(() -> new LibraryArtifact("org.example", value, "1.0", SHA, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LibraryArtifact("org.example", "lib", value, SHA, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"org..example", ".org", "org.", "org/example", "org.ex ample", "org.-x"})
    void refuse_un_groupId_mal_forme(String groupId) {
        assertThatThrownBy(() -> new LibraryArtifact(groupId, "lib", "1.0", SHA, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "BCB1F51E36F940867E83342F9EFBF5968AC44A6BEF4D397BB4AF7B17B45CD2FB",
            "bcb1f51e36f940867e83342f9efbf5968ac44a6bef4d397bb4af7b17b45cd2f"})
    void refuse_une_somme_mal_formee(String sha) {
        assertThatThrownBy(() -> new LibraryArtifact("org.example", "lib", "1.0", sha, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refuse_une_taille_hors_bornes() {
        assertThatThrownBy(() -> new LibraryArtifact("org.example", "lib", "1.0", SHA, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LibraryArtifact("org.example", "lib", "1.0", SHA, LibraryArtifact.MAX_SIZE + 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new LibraryArtifact("org.example", "lib", "1.0", SHA, LibraryArtifact.MAX_SIZE).size())
                .isEqualTo(LibraryArtifact.MAX_SIZE);
    }

    @Test
    void refuse_un_champ_nul() {
        assertThatThrownBy(() -> new LibraryArtifact(null, "lib", "1.0", SHA, 1)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new LibraryArtifact("org", null, "1.0", SHA, 1)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new LibraryArtifact("org", "lib", null, SHA, 1)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new LibraryArtifact("org", "lib", "1.0", null, 1)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void verifie_un_fichier_par_taille_et_somme(@TempDir Path dir) throws Exception {
        byte[] content = TestArtifacts.content(1000, 1);
        LibraryArtifact artifact = TestArtifacts.pinned("lib", content);
        Path file = dir.resolve("lib.jar");

        assertThat(ArtifactChecksums.isIntact(artifact, file)).as("absent").isFalse();
        Files.createDirectory(file);
        assertThat(ArtifactChecksums.isIntact(artifact, file)).as("dossier").isFalse();
        Path real = dir.resolve("real.jar");
        Files.write(real, content);
        assertThat(ArtifactChecksums.isIntact(artifact, real)).isTrue();
        byte[] altered = content.clone();
        altered[500] ^= 1;
        Files.write(real, altered);
        assertThat(ArtifactChecksums.isIntact(artifact, real)).as("meme taille, contenu modifie").isFalse();
        Files.write(real, new byte[999]);
        assertThat(ArtifactChecksums.isIntact(artifact, real)).as("taille differente").isFalse();
    }
}
