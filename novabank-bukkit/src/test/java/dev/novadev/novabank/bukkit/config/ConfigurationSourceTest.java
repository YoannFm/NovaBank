package dev.novadev.novabank.bukkit.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.bukkit.testing.RecordingLogger;
import dev.novadev.novabank.bukkit.testing.TestConfigs;
import dev.novadev.novabank.config.ConfigLoadResult;
import dev.novadev.novabank.config.NovaBankConfigLoader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigurationSourceTest {

    @TempDir
    Path directory;

    private final RecordingLogger log = new RecordingLogger();

    @Test
    void premier_demarrage_cree_le_fichier_par_defaut_qui_charge_sans_erreur_ni_avertissement() {
        Path file = directory.resolve("config.yml");

        ConfigLoadResult result = source(file, TestConfigs.defaultYaml()).load();

        assertThat(file).exists();
        assertThat(log.messages()).singleElement()
                .satisfies(message -> assertThat(message).contains("par defaut cree"));
        assertThat(result.config()).isPresent();
        assertThat(result.report().issues()).isEmpty();
    }

    @Test
    void un_fichier_valide_donne_une_configuration() throws IOException {
        Path file = directory.resolve("config.yml");
        Files.writeString(file, TestConfigs.defaultYaml());

        ConfigLoadResult result = source(file, "inutilise: 1").load();

        assertThat(result.config()).isPresent();
        assertThat(log.messages()).isEmpty();
    }

    @Test
    void un_yaml_invalide_devient_une_erreur_sans_reprendre_la_ligne_fautive() throws IOException {
        Path file = directory.resolve("config.yml");
        String secret = "MotDePasseSecret42";
        Files.writeString(file, "storage:\n  sql:\n    password: \"" + secret + "\n    other: [\n");

        ConfigLoadResult result = source(file, "inutilise: 1").load();

        assertThat(result.config()).isEmpty();
        assertThat(result.report().errors()).singleElement().satisfies(issue -> {
            assertThat(issue.path()).isEqualTo("config.yml");
            assertThat(issue.message()).contains("syntaxe YAML invalide").doesNotContain(secret);
        });
    }

    private ConfigurationSource source(Path file, String defaults) {
        ConfigFile configFile = new ConfigFile(file,
                () -> new ByteArrayInputStream(defaults.getBytes(StandardCharsets.UTF_8)));
        return new ConfigurationSource(configFile,
                new NovaBankConfigLoader(new BukkitItemAvailability(id -> true, name -> false)), log.logger());
    }

    @Test
    void une_config_de_version_1_recoit_les_nouvelles_cles_sans_toucher_aux_valeurs_ni_aux_sections_libres()
            throws IOException {
        Path file = directory.resolve("config.yml");
        String old = "config-version: 1\nlicense:\n  key: \"ANCIENNE-CLE\"\n"
                + "limits:\n  cooldown-ms: 900\n"
                + "banks:\n  roles:\n    player:\n      deposit: [owner]\n      withdraw: [owner]\n"
                + "items:\n  - key: minecraft:stone\n    price: \"2\"\n"
                + "ancienne-option: true\n";
        Files.writeString(file, old);

        ConfigLoadResult result = source(file, TestConfigs.defaultYaml()).load();

        assertThat(result.config()).isPresent();
        assertThat(result.config().get().limits().cooldownMs()).isEqualTo(900);
        assertThat(result.config().get().items()).singleElement()
                .satisfies(item -> assertThat(item.key().toString()).isEqualTo("minecraft:stone"));
        YamlConfiguration merged = YamlConfiguration.loadConfiguration(file.toFile());
        assertThat(merged.getInt("config-version")).isEqualTo(7);
        assertThat(merged.contains("license")).isFalse();
        assertThat(merged.getInt("limits.cooldown-ms")).isEqualTo(900);
        assertThat(merged.getInt("limits.max-quantity-per-operation")).isEqualTo(2304);
        // gui.buttons appartient a l'administrateur (l'editeur de GUI y ecrit "slot") : rien n'y est ajoute, le defaut vient du parseur.
        assertThat(merged.contains("gui.buttons.close.column")).isFalse();
        assertThat(result.config().get().gui().buttons().get(dev.novadev.novabank.config.model.GuiButton.CLOSE).icon().toString())
                .isEqualTo("minecraft:barrier");
        assertThat(merged.getStringList("banks.roles.player.deposit")).containsExactly("owner");
        assertThat(merged.getList("items")).hasSize(1);
        assertThat(merged.getBoolean("ancienne-option")).isTrue();
        try (var backups = Files.list(directory.resolve("backup"))) {
            assertThat(backups.toList()).singleElement()
                    .satisfies(backup -> assertThat(Files.readString(backup)).isEqualTo(old));
        }
        assertThat(log.messages()).anySatisfy(line -> assertThat(line).contains("mis a jour (version 1 -> 7)")
                .contains("retiree(s) car supprimee(s) de NovaBank [license]").contains("ancienne-option")
                .contains("ne conserve pas les commentaires"));
    }

    @Test
    void une_config_de_version_2_perd_sa_section_license_une_seule_fois_et_garde_les_valeurs() throws IOException {
        Path file = directory.resolve("config.yml");
        String old = TestConfigs.replaceOnce(TestConfigs.replaceOnce(TestConfigs.defaultYaml(),
                "config-version: 7", "config-version: 2"), "  cooldown-ms: 500", "  cooldown-ms: 900")
                + "\n# Licence\nlicense:\n  # Cle de licence\n  key: \"ANCIENNE-CLE\"\n  grace-days: 5\n";
        Files.writeString(file, old);

        ConfigLoadResult first = source(file, TestConfigs.defaultYaml()).load();

        assertThat(first.config()).isPresent();
        assertThat(first.report().issues()).isEmpty();
        assertThat(first.config().get().limits().cooldownMs()).isEqualTo(900);
        String updated = Files.readString(file);
        assertThat(updated).doesNotContain("license").doesNotContain("ANCIENNE-CLE").doesNotContain("Licence")
                .contains("config-version: 7").contains("cooldown-ms: 900");
        try (var backups = Files.list(directory.resolve("backup"))) {
            assertThat(backups.toList()).singleElement()
                    .satisfies(backup -> assertThat(Files.readString(backup)).isEqualTo(old));
        }
        assertThat(log.messages()).singleElement().satisfies(line -> assertThat(line)
                .contains("mis a jour (version 2 -> 7) : 0 cle(s) ajoutee(s), 1 cle(s) retiree(s)")
                .doesNotContain("obsolete"));

        ConfigLoadResult second = source(file, TestConfigs.defaultYaml()).load();

        assertThat(second.report().issues()).isEmpty();
        assertThat(Files.readString(file)).isEqualTo(updated);
        assertThat(log.messages()).hasSize(1);
    }
}
