package dev.novadev.novabank.storage.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.novadev.novabank.core.repository.StorageException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Mono-serveur (D4) : un second processus (second serveur) ou une seconde ouverture dans le meme
 * processus est refusee tant que le stockage est ouvert ; le verrou est libere a la fermeture.
 */
class SecondProcessTest {

    @TempDir
    Path directory;

    @Test
    void un_second_processus_est_refuse_avec_un_message_clair_puis_accepte_apres_fermeture() throws Exception {
        try (FileStorage first = FileTestSupport.open(directory, FileFormat.JSON)) {
            assertThat(first.content().isEmpty()).isTrue();
            Result refused = runSecondProcess();
            assertThat(refused.exitCode()).as(refused.output()).isEqualTo(2);
            assertThat(refused.output()).contains("deja utilisees par un autre serveur")
                    .contains("processus " + ProcessHandle.current().pid())
                    .contains("un stockage YAML/JSON ne sert qu'un seul serveur");
            assertThatThrownBy(() -> FileTestSupport.open(directory, FileFormat.JSON))
                    .isInstanceOf(StorageException.class).hasMessageContaining("deja utilisees");
        }
        Result accepted = runSecondProcess();
        assertThat(accepted.exitCode()).as(accepted.output()).isZero();
    }

    @Test
    void json_et_yaml_ont_des_fichiers_distincts_dans_le_meme_dossier() {
        try (FileStorage json = FileTestSupport.open(directory, FileFormat.JSON);
             FileStorage yaml = FileTestSupport.open(directory, FileFormat.YAML)) {
            assertThat(json.journalFile()).isNotEqualTo(yaml.journalFile());
        }
    }

    private Result runSecondProcess() throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process process = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
                LockProbe.class.getName(), directory.toString()).redirectErrorStream(true).start();
        assertThat(process.waitFor(60, TimeUnit.SECONDS)).isTrue();
        return new Result(process.exitValue(), new String(process.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8));
    }

    private record Result(int exitCode, String output) {
    }

    /** Second processus : tente d'ouvrir le stockage JSON du dossier. */
    public static final class LockProbe {

        private LockProbe() {
        }

        /**
         * @param args dossier
         */
        public static void main(String[] args) {
            try (FileStorage storage = FileTestSupport.open(Path.of(args[0]), FileFormat.JSON)) {
                System.out.println("ouvert, version " + storage.schemaVersion());
            } catch (StorageException refused) {
                System.out.println(refused.getMessage());
                System.exit(2);
            }
        }
    }
}
