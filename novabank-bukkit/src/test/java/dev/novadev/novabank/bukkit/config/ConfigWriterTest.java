package dev.novadev.novabank.bukkit.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigWriterTest {

    @Test
    void sauvegarde_l_ancien_fichier_avant_d_ecrire_le_nouveau(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("config.yml");
        Files.writeString(file, "language: fr\n");

        Path backup = new ConfigWriter(file).write("language: en\n", "gui");

        assertThat(Files.readString(file)).isEqualTo("language: en\n");
        assertThat(Files.readString(backup)).isEqualTo("language: fr\n");
        assertThat(backup.getFileName().toString()).startsWith("config-").endsWith("-gui.yml");
        try (Stream<Path> files = Files.list(dir)) {
            assertThat(files.map(path -> path.getFileName().toString())).doesNotContain("config.yml.edit");
        }
    }

    @Test
    void sans_fichier_a_sauvegarder_rien_n_est_ecrit(@TempDir Path dir) {
        Path file = dir.resolve("config.yml");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new ConfigWriter(file).write("x: 1\n", "gui"))
                .isInstanceOf(IOException.class);
        assertThat(file).doesNotExist();
    }
}
