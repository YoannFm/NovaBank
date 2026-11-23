package dev.novadev.novabank.libraries.cache;

import static dev.novadev.novabank.libraries.testing.FakeRepositoryServer.delayed;
import static dev.novadev.novabank.libraries.testing.FakeRepositoryServer.ok;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.novadev.novabank.libraries.LibraryException;
import dev.novadev.novabank.libraries.artifact.ArtifactChecksums;
import dev.novadev.novabank.libraries.artifact.LibraryArtifact;
import dev.novadev.novabank.libraries.download.ArtifactDownloader;
import dev.novadev.novabank.libraries.download.DownloadSettings;
import dev.novadev.novabank.libraries.testing.FakeRepositoryServer;
import dev.novadev.novabank.libraries.testing.RecordingLogger;
import dev.novadev.novabank.libraries.testing.TestArtifacts;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(30)
class LocalLibraryCacheTest {

    private static final byte[] CONTENT = TestArtifacts.content(300_000, 7);
    private static final LibraryArtifact ARTIFACT = TestArtifacts.pinned("driver", CONTENT);

    private final FakeRepositoryServer server = new FakeRepositoryServer();
    private final RecordingLogger logger = new RecordingLogger();

    @TempDir
    Path root;

    @AfterEach
    void stopServer() {
        server.close();
    }

    private Path libs() {
        return root.resolve("plugins/NovaBank/libs");
    }

    private LocalLibraryCache cache() {
        ArtifactDownloader downloader = new ArtifactDownloader(new DownloadSettings(List.of(server.baseUri()),
                Duration.ofSeconds(2), Duration.ofSeconds(2), 2, Duration.ZERO), logger);
        return new LocalLibraryCache(libs(), downloader, logger);
    }

    private List<Path> parts() throws IOException {
        try (Stream<Path> files = Files.list(libs())) {
            return files.filter(file -> file.getFileName().toString().endsWith(".part")).toList();
        }
    }

    @Test
    void telecharge_dans_le_dossier_cree_sans_laisser_de_fichier_partiel() throws IOException {
        server.serve(ARTIFACT, ok(CONTENT));

        Path jar = cache().resolve(ARTIFACT);

        assertThat(jar).isEqualTo(libs().toAbsolutePath().resolve("driver-1.0.jar"));
        assertThat(Files.readAllBytes(jar)).isEqualTo(CONTENT);
        assertThat(parts()).isEmpty();
    }

    @Test
    void somme_invalide_ni_fichier_final_ni_fichier_partiel() throws IOException {
        byte[] forged = CONTENT.clone();
        forged[1] ^= 1;
        server.serve(ARTIFACT, ok(forged));

        assertThatThrownBy(() -> cache().resolve(ARTIFACT))
                .isInstanceOf(LibraryException.class)
                .hasMessageContaining("somme SHA-256 invalide");
        assertThat(libs().resolve(ARTIFACT.fileName())).doesNotExist();
        assertThat(parts()).isEmpty();
    }

    @Test
    void cache_valide_aucune_requete_reseau() throws IOException {
        Files.createDirectories(libs());
        Files.write(libs().resolve(ARTIFACT.fileName()), CONTENT);

        Path jar = cache().resolve(ARTIFACT);

        assertThat(Files.readAllBytes(jar)).isEqualTo(CONTENT);
        assertThat(server.totalRequests()).isZero();
        assertThat(logger.lines()).isEmpty();
    }

    @Test
    void cache_corrompu_retelecharge() throws IOException {
        Files.createDirectories(libs());
        byte[] corrupted = CONTENT.clone();
        corrupted[CONTENT.length - 1] ^= 1;
        Files.write(libs().resolve(ARTIFACT.fileName()), corrupted);
        server.serve(ARTIFACT, ok(CONTENT));

        Path jar = cache().resolve(ARTIFACT);

        assertThat(Files.readAllBytes(jar)).isEqualTo(CONTENT);
        assertThat(server.requests(ARTIFACT)).isEqualTo(1);
        assertThat(logger.lines()).anyMatch(line -> line.startsWith("WARN") && line.contains("corrompu"));
    }

    @Test
    void deux_threads_meme_artefact_un_seul_telechargement() throws Exception {
        server.serve(ARTIFACT, delayed(200, ok(CONTENT)));
        LocalLibraryCache cache = cache();

        List<Path> results = concurrently(() -> cache.resolve(ARTIFACT), () -> cache.resolve(ARTIFACT));

        assertThat(results).containsOnly(libs().toAbsolutePath().resolve(ARTIFACT.fileName()));
        assertThat(server.requests(ARTIFACT)).isEqualTo(1);
        assertThat(ArtifactChecksums.isIntact(ARTIFACT, results.get(0))).isTrue();
        assertThat(parts()).isEmpty();
    }

    @Test
    void deux_processus_meme_dossier_fichier_final_correct() throws Exception {
        server.serve(ARTIFACT, delayed(200, ok(CONTENT)));
        LocalLibraryCache first = cache();
        LocalLibraryCache second = cache();

        List<Path> results = concurrently(() -> first.resolve(ARTIFACT), () -> second.resolve(ARTIFACT));

        assertThat(results.get(0)).isEqualTo(results.get(1));
        assertThat(server.requests(ARTIFACT)).isEqualTo(2);
        assertThat(Files.readAllBytes(results.get(0))).isEqualTo(CONTENT);
        assertThat(parts()).isEmpty();
    }

    @Test
    void supprime_les_fichiers_partiels_abandonnes_mais_pas_les_recents() throws IOException {
        Files.createDirectories(libs());
        Path stale = Files.write(libs().resolve("driver-1.0.jar.111.part"), new byte[10]);
        Files.setLastModifiedTime(stale, FileTime.from(Instant.now()
                .minus(LocalLibraryCache.STALE_PART_AGE).minusSeconds(60)));
        Path recent = Files.write(libs().resolve("driver-1.0.jar.222.part"), new byte[10]);
        Path otherArtifact = Files.write(libs().resolve("other-1.0.jar.333.part"), new byte[10]);
        Files.setLastModifiedTime(otherArtifact, FileTime.from(Instant.EPOCH));
        server.serve(ARTIFACT, ok(CONTENT));

        cache().resolve(ARTIFACT);

        assertThat(stale).doesNotExist();
        assertThat(recent).exists();
        assertThat(otherArtifact).exists();
    }

    @Test
    void dossier_inutilisable_erreur_claire() throws IOException {
        Files.createDirectories(libs().getParent());
        Files.write(libs(), new byte[1]);

        assertThatThrownBy(() -> cache().resolve(ARTIFACT))
                .isInstanceOf(LibraryException.class)
                .hasMessageContaining("Dossier des bibliotheques inutilisable");
    }

    @Test
    void resout_une_liste_dans_l_ordre() {
        byte[] otherContent = TestArtifacts.content(1000, 8);
        LibraryArtifact other = TestArtifacts.pinned("dependency", otherContent);
        server.serve(ARTIFACT, ok(CONTENT));
        server.serve(other, ok(otherContent));

        List<Path> jars = cache().resolveAll(List.of(ARTIFACT, other));

        assertThat(jars).extracting(jar -> jar.getFileName().toString())
                .containsExactly("driver-1.0.jar", "dependency-1.0.jar");
    }

    private static List<Path> concurrently(Callable<Path> first, Callable<Path> second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CyclicBarrier start = new CyclicBarrier(2);
            List<Future<Path>> futures = new ArrayList<>();
            for (Callable<Path> task : List.of(first, second)) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            List<Path> results = new ArrayList<>();
            for (Future<Path> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }
}
