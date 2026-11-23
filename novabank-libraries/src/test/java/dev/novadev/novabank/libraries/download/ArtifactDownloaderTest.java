package dev.novadev.novabank.libraries.download;

import static dev.novadev.novabank.libraries.testing.FakeRepositoryServer.chunked;
import static dev.novadev.novabank.libraries.testing.FakeRepositoryServer.hangAfterPartialBody;
import static dev.novadev.novabank.libraries.testing.FakeRepositoryServer.hangBeforeHeaders;
import static dev.novadev.novabank.libraries.testing.FakeRepositoryServer.ok;
import static dev.novadev.novabank.libraries.testing.FakeRepositoryServer.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.novadev.novabank.libraries.LibraryException;
import dev.novadev.novabank.libraries.artifact.LibraryArtifact;
import dev.novadev.novabank.libraries.testing.FakeRepositoryServer;
import dev.novadev.novabank.libraries.testing.RecordingLogger;
import dev.novadev.novabank.libraries.testing.TestArtifacts;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(30)
class ArtifactDownloaderTest {

    private static final byte[] CONTENT = TestArtifacts.content(200_000, 42);
    private static final LibraryArtifact ARTIFACT = TestArtifacts.pinned("driver", CONTENT);

    private final FakeRepositoryServer primary = new FakeRepositoryServer();
    private final FakeRepositoryServer mirror = new FakeRepositoryServer();
    private final RecordingLogger logger = new RecordingLogger();

    @TempDir
    Path dir;

    @AfterEach
    void stopServers() {
        primary.close();
        mirror.close();
    }

    private ArtifactDownloader downloader(URI... repositories) {
        return new ArtifactDownloader(new DownloadSettings(List.of(repositories), Duration.ofSeconds(2),
                Duration.ofMillis(400), 3, Duration.ofMillis(10)), logger);
    }

    private Path target() {
        return dir.resolve("driver.part");
    }

    @Test
    void telecharge_et_verifie_le_contenu() throws IOException {
        primary.serve(ARTIFACT, ok(CONTENT));

        downloader(primary.baseUri()).download(ARTIFACT, target());

        assertThat(Files.readAllBytes(target())).isEqualTo(CONTENT);
        assertThat(primary.requests(ARTIFACT)).isEqualTo(1);
        assertThat(logger.lines()).anyMatch(line -> line.contains("somme SHA-256 verifiee"));
    }

    @Test
    void accepte_un_corps_sans_longueur_annoncee() throws IOException {
        primary.serve(ARTIFACT, chunked(CONTENT));

        downloader(primary.baseUri()).download(ARTIFACT, target());

        assertThat(Files.readAllBytes(target())).isEqualTo(CONTENT);
    }

    @Test
    void refuse_une_somme_invalide_sans_reessayer_le_meme_depot() {
        byte[] forged = CONTENT.clone();
        forged[0] ^= 1;
        primary.serve(ARTIFACT, ok(forged));

        assertThatThrownBy(() -> downloader(primary.baseUri()).download(ARTIFACT, target()))
                .isInstanceOf(LibraryException.class)
                .hasMessageContaining("dev.example:driver:1.0")
                .hasMessageContaining("somme SHA-256 invalide")
                .hasMessageContaining(ARTIFACT.sha256());
        assertThat(primary.requests(ARTIFACT)).isEqualTo(1);
    }

    @Test
    void erreur_404_definitive_pour_le_depot() {
        assertThatThrownBy(() -> downloader(primary.baseUri()).download(ARTIFACT, target()))
                .isInstanceOf(LibraryException.class)
                .hasMessageContaining("HTTP 404");
        assertThat(primary.requests(ARTIFACT)).isEqualTo(1);
    }

    @Test
    void erreur_500_reessayee_puis_echec_clair_apres_n_essais() {
        primary.serve(ARTIFACT, status(500));

        assertThatThrownBy(() -> downloader(primary.baseUri()).download(ARTIFACT, target()))
                .isInstanceOf(LibraryException.class)
                .hasMessageContaining("essai 3 : HTTP 500");
        assertThat(primary.requests(ARTIFACT)).isEqualTo(3);
        assertThat(logger.lines()).filteredOn(line -> line.startsWith("WARN")).hasSize(3);
    }

    @Test
    void erreur_temporaire_puis_succes() throws IOException {
        primary.serve(ARTIFACT, status(503), ok(CONTENT));

        downloader(primary.baseUri()).download(ARTIFACT, target());

        assertThat(Files.readAllBytes(target())).isEqualTo(CONTENT);
        assertThat(primary.requests(ARTIFACT)).isEqualTo(2);
    }

    @Test
    void trop_de_requetes_429_reessaye() throws IOException {
        primary.serve(ARTIFACT, status(429), ok(CONTENT));

        downloader(primary.baseUri()).download(ARTIFACT, target());

        assertThat(primary.requests(ARTIFACT)).isEqualTo(2);
    }

    @Test
    void acces_refuse_403_definitif_pour_le_depot() {
        primary.serve(ARTIFACT, status(403));

        assertThatThrownBy(() -> downloader(primary.baseUri()).download(ARTIFACT, target()))
                .isInstanceOf(LibraryException.class)
                .hasMessageContaining("HTTP 403");
        assertThat(primary.requests(ARTIFACT)).isEqualTo(1);
    }

    @Test
    void delai_sans_en_tetes_reessaye_puis_echec() {
        primary.serve(ARTIFACT, hangBeforeHeaders());
        ArtifactDownloader downloader = new ArtifactDownloader(new DownloadSettings(List.of(primary.baseUri()),
                Duration.ofSeconds(2), Duration.ofMillis(300), 2, Duration.ZERO), logger);

        assertThatThrownBy(() -> downloader.download(ARTIFACT, target()))
                .isInstanceOf(LibraryException.class)
                .hasMessageContaining("essai 2 : delai");
        assertThat(primary.requests(ARTIFACT)).isEqualTo(2);
    }

    @Test
    void transfert_bloque_en_cours_de_corps_abandonne() {
        primary.serve(ARTIFACT, hangAfterPartialBody(CONTENT));

        assertThatThrownBy(() -> downloader(primary.baseUri()).download(ARTIFACT, target()))
                .isInstanceOf(LibraryException.class)
                .hasMessageContaining("delai de lecture depasse");
        assertThat(primary.requests(ARTIFACT)).isEqualTo(3);
    }

    @Test
    void repli_sur_le_miroir_apres_echec_du_depot_principal() throws IOException {
        primary.serve(ARTIFACT, status(500));
        mirror.serve(ARTIFACT, ok(CONTENT));

        downloader(primary.baseUri(), mirror.baseUri()).download(ARTIFACT, target());

        assertThat(Files.readAllBytes(target())).isEqualTo(CONTENT);
        assertThat(primary.requests(ARTIFACT)).isEqualTo(3);
        assertThat(mirror.requests(ARTIFACT)).isEqualTo(1);
    }

    @Test
    void repli_sur_le_miroir_si_le_principal_est_injoignable() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        mirror.serve(ARTIFACT, ok(CONTENT));

        downloader(URI.create("http://127.0.0.1:" + closedPort + "/repo/"), mirror.baseUri())
                .download(ARTIFACT, target());

        assertThat(Files.readAllBytes(target())).isEqualTo(CONTENT);
        assertThat(logger.lines()).anyMatch(line -> line.contains("connexion impossible"));
    }

    @Test
    void miroir_malveillant_ignore_au_profit_du_suivant() throws IOException {
        byte[] forged = CONTENT.clone();
        forged[10] ^= 1;
        mirror.serve(ARTIFACT, ok(forged));
        primary.serve(ARTIFACT, ok(CONTENT));

        downloader(mirror.baseUri(), primary.baseUri()).download(ARTIFACT, target());

        assertThat(Files.readAllBytes(target())).isEqualTo(CONTENT);
    }

    @Test
    void refuse_un_fichier_annonce_trop_gros_sans_le_lire() {
        byte[] bigger = Arrays.copyOf(CONTENT, CONTENT.length + 1);
        primary.serve(ARTIFACT, ok(bigger));

        assertThatThrownBy(() -> downloader(primary.baseUri()).download(ARTIFACT, target()))
                .isInstanceOf(LibraryException.class)
                .hasMessageContaining("fichier trop gros");
        assertThat(primary.requests(ARTIFACT)).isEqualTo(1);
    }

    @Test
    void refuse_un_fichier_trop_gros_sans_longueur_annoncee() throws IOException {
        primary.serve(ARTIFACT, chunked(Arrays.copyOf(CONTENT, CONTENT.length * 3)));

        assertThatThrownBy(() -> downloader(primary.baseUri()).download(ARTIFACT, target()))
                .isInstanceOf(LibraryException.class)
                .hasMessageContaining("fichier trop gros");
        assertThat(Files.size(target())).isLessThanOrEqualTo(ARTIFACT.size());
    }

    @Test
    void refuse_un_fichier_tronque() {
        primary.serve(ARTIFACT, chunked(Arrays.copyOf(CONTENT, CONTENT.length - 1)));

        assertThatThrownBy(() -> downloader(primary.baseUri()).download(ARTIFACT, target()))
                .isInstanceOf(LibraryException.class)
                .hasMessageContaining("transfert incomplet");
        assertThat(primary.requests(ARTIFACT)).isEqualTo(3);
    }

    @Test
    void fichier_de_destination_inutilisable() throws IOException {
        primary.serve(ARTIFACT, ok(CONTENT));
        Path directoryInsteadOfFile = Files.createDirectory(dir.resolve("occupied"));

        assertThatThrownBy(() -> downloader(primary.baseUri()).download(ARTIFACT, directoryInsteadOfFile))
                .isInstanceOf(LibraryException.class)
                .hasMessageContaining("fichier temporaire inutilisable");
        assertThat(primary.requests(ARTIFACT)).isZero();
    }

    @Test
    void interruption_du_thread_appelant() {
        primary.serve(ARTIFACT, hangBeforeHeaders());
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> downloader(primary.baseUri()).download(ARTIFACT, target()))
                    .isInstanceOf(LibraryException.class)
                    .hasMessageContaining("interrompu");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }
}
