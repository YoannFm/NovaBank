package dev.novadev.novabank.storage.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import dev.novadev.novabank.core.delivery.UndeliveredPolicy;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Arret brutal : une JVM fille ecrit en continu dans une base SQLite et est tuee
 * ({@code destroyForcibly}, SIGKILL) a un instant aleatoire, cinq fois de suite sur la
 * meme base. Apres chaque arret, la base est rouverte et doit etre exactement coherente
 * avec son journal.
 */
class CrashRecoveryTest {

    private static final int KILLS = 5;
    private static final long READY_TIMEOUT_SECONDS = 30L;

    @TempDir
    Path directory;

    @Test
    void des_arrets_brutaux_ne_laissent_aucune_operation_a_moitie_appliquee() throws Exception {
        Path database = directory.resolve("crash.db");
        Random random = new Random();
        long previousOperations = 0L;
        List<String> rounds = new ArrayList<>();

        for (int round = 1; round <= KILLS; round++) {
            UndeliveredPolicy policy = UndeliveredPolicy.values()[round % UndeliveredPolicy.values().length];
            Path errors = directory.resolve("worker-" + round + ".err");
            Process worker = startWorker(database, random.nextLong(), policy, errors);
            String ready = awaitReady(worker, errors);
            long delayMs = 300L + random.nextInt(1200);
            Thread.sleep(delayMs);
            if (!worker.isAlive()) {
                fail("La JVM fille s'est arretee seule (code " + worker.exitValue() + ") :\n" + Files.readString(errors));
            }
            worker.destroyForcibly();
            assertThat(worker.waitFor(20, TimeUnit.SECONDS)).isTrue();
            long walBytes = Files.size(Path.of(database + "-wal"));

            long operations = verifyInvariants(database);
            assertThat(operations).as("la JVM fille doit avoir travaille avant d'etre tuee").isGreaterThan(previousOperations);
            rounds.add("arret " + round + " apres " + delayMs + " ms (" + ready + ") : WAL de " + walBytes + " octets a la reprise, "
                    + operations + " operations");
            previousOperations = operations;
        }

        try (SqlStorage storage = SqliteTestSupport.open(database)) {
            assertThat(storage.schemaVersion()).isEqualTo(SchemaMigrator.CURRENT_VERSION);
        }
        System.out.println(String.join(System.lineSeparator(), rounds));
    }

    private static Process startWorker(Path database, long seed, UndeliveredPolicy policy, Path errors)
            throws IOException {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        ProcessBuilder builder = new ProcessBuilder(java, "-Xmx128m", "--enable-native-access=ALL-UNNAMED", "-cp", System.getProperty("java.class.path"),
                CrashWorker.class.getName(), database.toString(), Long.toString(seed), policy.name());
        builder.redirectError(errors.toFile());
        return builder.start();
    }

    private static String awaitReady(Process worker, Path errors) throws Exception {
        BufferedReader output = new BufferedReader(new InputStreamReader(worker.getInputStream(), StandardCharsets.UTF_8));
        CompletableFuture<String> line = CompletableFuture.supplyAsync(() -> {
            try {
                return output.readLine();
            } catch (IOException failure) {
                return null;
            }
        });
        try {
            String ready = line.get(READY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (ready == null || !ready.startsWith("READY")) {
                worker.destroyForcibly();
                fail("La JVM fille n'a pas demarre (" + ready + ") :\n" + Files.readString(errors));
            }
            return ready;
        } catch (TimeoutException timeout) {
            worker.destroyForcibly();
            throw timeout;
        }
    }

    private static long verifyInvariants(Path database) throws SQLException {
        try (Connection connection = SqliteTestSupport.rawConnection(database)) {
            assertThat(JournalInvariants.violations(connection, SqlStorage.DEFAULT_TABLE_PREFIX)).isEmpty();
            return Long.parseLong(JournalInvariants.single(connection, "SELECT COUNT(*) FROM novabank_operations"));
        }
    }
}
