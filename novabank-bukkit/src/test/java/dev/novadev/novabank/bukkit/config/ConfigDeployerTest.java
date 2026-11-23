package dev.novadev.novabank.bukkit.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.bukkit.testing.RecordingLogger;
import dev.novadev.novabank.bukkit.testing.TestConfigs;
import dev.novadev.novabank.config.NovaBankConfigLoader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigDeployerTest {

    @TempDir
    Path directory;

    private Path file;
    private ConfigDeployer deployer;

    @BeforeEach
    void setUp() throws IOException {
        file = directory.resolve("config.yml");
        Files.writeString(file, TestConfigs.defaultYaml());
        ConfigurationSource source = new ConfigurationSource(new ConfigFile(file,
                () -> new ByteArrayInputStream(TestConfigs.defaultYaml().getBytes(StandardCharsets.UTF_8))),
                new NovaBankConfigLoader(new BukkitItemAvailability(id -> true, name -> false)), new RecordingLogger().logger());
        deployer = new ConfigDeployer(file, new ConfigWriter(file), source::validate);
    }

    @Test
    void un_changement_valide_sauvegarde_l_ancien_fichier_puis_l_ecrit_en_gardant_le_reste() throws IOException {
        String before = Files.readString(file);

        ConfigDeployer.Outcome outcome = deployer.apply(Map.of("gui", "gui:\n  rows: 4"), "gui");

        assertThat(outcome.applied()).isTrue();
        assertThat(outcome.changed()).isTrue();
        String after = Files.readString(file);
        assertThat(after).contains("gui:\n  rows: 4").contains("config-version:").contains("storage:");
        try (Stream<Path> backups = Files.list(directory.resolve("backup"))) {
            assertThat(backups.toList()).singleElement().satisfies(backup -> {
                assertThat(backup.getFileName().toString()).endsWith("-gui.yml");
                assertThat(Files.readString(backup)).isEqualTo(before);
            });
        }
    }

    @Test
    void une_configuration_invalide_est_refusee_et_le_fichier_n_est_pas_touche() throws IOException {
        String before = Files.readString(file);

        ConfigDeployer.Outcome outcome = deployer.apply(Map.of("gui", "gui:\n  rows: 12"), "gui");

        assertThat(outcome.applied()).isFalse();
        assertThat(outcome.errors()).singleElement().satisfies(error -> assertThat(error).contains("gui.rows"));
        assertThat(Files.readString(file)).isEqualTo(before);
        assertThat(directory.resolve("backup")).doesNotExist();
    }

    @Test
    void une_modification_locale_peut_toucher_storage_et_reste_validee() throws IOException {
        ConfigDeployer.Outcome edited = deployer.edit(text -> YamlPaths.set(text, "storage.sql.pool.max-size", "20"), "settings");

        assertThat(edited.applied()).isTrue();
        assertThat(edited.changed()).isTrue();
        assertThat(Files.readString(file)).contains("max-size: 20").contains("config-version:");

        String before = Files.readString(file);
        ConfigDeployer.Outcome refused = deployer.edit(text -> YamlPaths.set(text, "storage.sql.pool.max-size", "500"),
                "settings");

        assertThat(refused.applied()).isFalse();
        assertThat(refused.errors()).singleElement().satisfies(error -> assertThat(error).contains("max-size"));
        assertThat(Files.readString(file)).isEqualTo(before);
    }

    @Test
    void un_changement_sans_effet_n_ecrit_rien() throws IOException {
        String same = YamlSections.extract(Files.readString(file), "language").orElseThrow();

        ConfigDeployer.Outcome outcome = deployer.apply(Map.of("language", same), "gui");

        assertThat(outcome.applied()).isTrue();
        assertThat(outcome.changed()).isFalse();
        assertThat(directory.resolve("backup")).doesNotExist();
    }

    @Test
    void les_sections_propres_au_serveur_ne_se_deploient_pas() throws IOException {
        String before = Files.readString(file);

        for (String key : List.of("storage", "redis", "server", "config-version")) {
            ConfigDeployer.Outcome outcome = deployer.apply(Map.of(key, key + ":\n  type: YAML"), "remote");
            assertThat(outcome.applied()).as(key).isFalse();
        }
        assertThat(Files.readString(file)).isEqualTo(before);
    }

    @Test
    void envoi_puis_reception_entre_deux_serveurs_reproduit_la_section_et_sauvegarde_chez_le_recepteur()
            throws IOException {
        deployer.apply(Map.of("gui", "gui:\n  rows: 3"), "gui");
        String payload = deployer.payloadFor(List.of("gui", "storage")).orElseThrow();

        Path otherDir = Files.createDirectory(directory.resolve("autre"));
        Path other = otherDir.resolve("config.yml");
        Files.writeString(other, TestConfigs.defaultYaml());
        ConfigurationSource source = new ConfigurationSource(new ConfigFile(other,
                () -> new ByteArrayInputStream(TestConfigs.defaultYaml().getBytes(StandardCharsets.UTF_8))),
                new NovaBankConfigLoader(new BukkitItemAvailability(id -> true, name -> false)), new RecordingLogger().logger());
        ConfigDeployer receiver = new ConfigDeployer(other, new ConfigWriter(other), source::validate);

        ConfigDeployer.Outcome outcome = receiver.receive(payload);

        assertThat(outcome.applied()).isTrue();
        assertThat(Files.readString(other)).contains("gui:\n  rows: 3");
        assertThat(otherDir.resolve("backup")).isDirectory();
        assertThat(ConfigPayload.decode(payload).orElseThrow()).containsOnlyKeys("gui"); // storage n'est pas partage
    }

    @Test
    void un_message_falsifie_ou_trop_gros_est_refuse() throws IOException {
        String before = Files.readString(file);
        Map<String, String> sneaky = new LinkedHashMap<>();
        sneaky.put("gui", "gui:\n  rows: 3\nstorage:\n  type: YAML");

        assertThat(deployer.receive("pas-du-base64!").applied()).isFalse();
        assertThat(deployer.receive("AAAA").applied()).isFalse();
        assertThat(ConfigPayload.decode(java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[] {1, 2, 3})))
                .isEmpty();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> ConfigPayload.encode(sneaky))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(Files.readString(file)).isEqualTo(before);
    }
}
