package dev.novadev.novabank.storage.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import dev.novadev.novabank.api.OperationId;
import dev.novadev.novabank.core.testing.ContentInvariants;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Arret brutal : une JVM fille ecrit en continu (compactions comprises) et est tuee
 * ({@code destroyForcibly}, SIGKILL) a un instant aleatoire, trois fois de suite sur les memes
 * donnees. Apres chaque arret : aucune operation confirmee perdue, journal et stocks coherents,
 * derniere ligne tronquee eventuelle geree.
 */
class FileCrashRecoveryTest {

    private static final int KILLS = 3;
    private static final long READY_TIMEOUT_SECONDS = 30L;

    @TempDir
    Path directory;

    @ParameterizedTest
    @EnumSource(FileFormat.class)
    void des_arrets_brutaux_ne_perdent_aucune_operation_confirmee(FileFormat format) throws Exception {
        Path data = directory.resolve(format.name());
        Random random = new Random();
        long previousOperations = 0L;
        List<String> rounds = new ArrayList<>();
        for (int round = 1; round <= KILLS; round++) {
            Set<OperationId> confirmed = ConcurrentHashMap.newKeySet();
            Path errors = directory.resolve(format + "-worker-" + round + ".err");
            Process worker = start(data, format, random.nextLong(), errors);
            CountDownLatch ready = new CountDownLatch(1);
            Thread reader = readOutput(worker, confirmed, ready);
            if (!ready.await(READY_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                worker.destroyForcibly();
                fail("La JVM fille n'a pas demarre :\n" + Files.readString(errors));
            }
            long delayMs = 400L + random.nextInt(1200);
            Thread.sleep(delayMs);
            if (!worker.isAlive()) {
                fail("La JVM fille s'est arretee seule (code " + worker.exitValue() + ") :\n" + Files.readString(errors));
            }
            worker.destroyForcibly();
            assertThat(worker.waitFor(20, TimeUnit.SECONDS)).isTrue();
            reader.join(10_000);
            long journalBytes = Files.size(data.resolve("novabank." + format.codec().extension() + ".journal"));

            long operations;
            try (FileStorage storage = FileTestSupport.open(data, format)) {
                List<OperationId> lost = confirmed.stream()
                        .filter(id -> storage.banks().findOperation(id).isEmpty()).toList();
                assertThat(lost).as("operations confirmees perdues").isEmpty();
                assertThat(ContentInvariants.violations(storage.content())).isEmpty();
                long[] count = {0L};
                storage.content().exportOperations(record -> count[0]++);
                operations = count[0];
            }
            assertThat(confirmed).as("la JVM fille doit avoir travaille").isNotEmpty();
            assertThat(operations).isGreaterThan(previousOperations);
            try (var files = Files.list(data)) {
                long torn = files.filter(file -> file.getFileName().toString().contains(".torn-")).count();
                rounds.add(format + " arret " + round + " apres " + delayMs + " ms : " + confirmed.size()
                        + " operations confirmees toutes retrouvees, journal de " + journalBytes + " octets a la reprise, "
                        + operations + " operations au total, " + torn + " ligne(s) tronquee(s) conservee(s) a ce jour");
            }
            previousOperations = operations;
        }
        System.out.println(String.join(System.lineSeparator(), rounds));
    }

    private static Process start(Path data, FileFormat format, long seed, Path errors) throws IOException {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        ProcessBuilder builder = new ProcessBuilder(java, "-Xmx256m", "-cp", System.getProperty("java.class.path"),
                FileCrashWorker.class.getName(), data.toString(), format.name(), Long.toString(seed));
        builder.redirectError(errors.toFile());
        return builder.start();
    }

    private static Thread readOutput(Process worker, Set<OperationId> confirmed, CountDownLatch ready) {
        Thread reader = new Thread(() -> {
            try (BufferedReader output = new BufferedReader(new InputStreamReader(worker.getInputStream(),
                    StandardCharsets.UTF_8))) {
                String line;
                while ((line = output.readLine()) != null) {
                    if (line.startsWith("READY")) {
                        ready.countDown();
                    } else if (line.startsWith("OK ")) {
                        confirmed.add(new OperationId(UUID.fromString(line.substring(3))));
                    }
                }
            } catch (IOException | IllegalArgumentException closed) {
                // Processus tue : fin de flux, eventuellement au milieu d'une ligne.
            }
        }, "crash-output");
        reader.start();
        return reader;
    }
}
