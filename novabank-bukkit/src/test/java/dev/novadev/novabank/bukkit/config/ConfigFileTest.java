package dev.novadev.novabank.bukkit.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigFileTest {

    @TempDir
    Path directory;

    @Test
    void le_fichier_par_defaut_est_copie_s_il_est_absent_dossiers_compris() throws Exception {
        Path target = directory.resolve("plugins/NovaBank/config.yml");
        ConfigFile file = new ConfigFile(target, () -> stream("a: 1\n"));

        assertThat(file.createIfAbsent()).isTrue();

        assertThat(Files.readString(target)).isEqualTo("a: 1\n");
        assertThat(file.read()).isEqualTo(Map.of("a", 1));
    }

    @Test
    void un_fichier_existant_n_est_jamais_ecrase() throws IOException {
        Path target = directory.resolve("config.yml");
        Files.writeString(target, "edite: true\n");
        ConfigFile file = new ConfigFile(target, () -> stream("a: 1\n"));

        assertThat(file.createIfAbsent()).isFalse();

        assertThat(Files.readString(target)).isEqualTo("edite: true\n");
    }

    @Test
    void une_ressource_par_defaut_absente_est_signalee() {
        ConfigFile file = new ConfigFile(directory.resolve("config.yml"), () -> null);

        assertThatThrownBy(file::createIfAbsent).isInstanceOf(IOException.class).hasMessageContaining("absente");
        assertThat(directory.resolve("config.yml")).doesNotExist();
    }

    @Test
    void un_yaml_invalide_leve_une_exception_de_configuration() throws IOException {
        Path target = directory.resolve("config.yml");
        Files.writeString(target, "a: [1, 2\n");

        assertThatThrownBy(() -> new ConfigFile(target, () -> null).read())
                .isInstanceOf(InvalidConfigurationException.class);
    }

    @Test
    void les_fichiers_fournis_en_francais_et_en_anglais_ont_les_memes_cles_et_valeurs() {
        YamlConfiguration french = bundled(ConfigFile.NAME);
        YamlConfiguration english = bundled(ConfigFile.ENGLISH);
        assertThat(english.getKeys(true)).containsExactlyInAnyOrderElementsOf(french.getKeys(true));
        for (String key : french.getKeys(true)) {
            if (!french.isConfigurationSection(key) && !key.equals("language") && !key.equals("items")) {
                assertThat(english.get(key)).as(key).isEqualTo(french.get(key));
            }
        }
        assertThat(english.getString("language")).isEqualTo("en");
        assertThat(english.getList("items")).hasSameSizeAs(french.getList("items"));
    }

    @Test
    void sans_api_de_commentaires_le_fichier_n_est_pas_retouche() throws Exception {
        Path target = directory.resolve("config.yml");
        Files.writeString(target, "language: en\n");
        ConfigFile file = new ConfigFile(target, () -> stream("language: fr\n"), () -> stream("language: en\n"));

        assertThat(file.localizeComments(new YamlFileUpdater("v", java.util.Set.of(), java.time.Clock.systemUTC())))
                .isEmpty();
        assertThat(Files.readString(target)).isEqualTo("language: en\n");
    }

    private static YamlConfiguration bundled(String name) {
        try (java.io.InputStream input = ConfigFileTest.class.getClassLoader().getResourceAsStream(name);
             java.io.Reader reader = new java.io.InputStreamReader(input, StandardCharsets.UTF_8)) {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(reader);
            return yaml;
        } catch (IOException | InvalidConfigurationException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static ByteArrayInputStream stream(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }
}
