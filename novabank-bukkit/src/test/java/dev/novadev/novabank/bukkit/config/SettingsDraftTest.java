package dev.novadev.novabank.bukkit.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.bukkit.testing.RecordingLogger;
import dev.novadev.novabank.bukkit.testing.TestConfigs;
import dev.novadev.novabank.config.NovaBankConfigLoader;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SettingsDraftTest {

    @TempDir
    Path directory;

    private String yaml;
    private SettingsDraft draft;
    private ConfigurationSource source;

    @BeforeEach
    void setUp() throws Exception {
        yaml = TestConfigs.defaultYaml();
        YamlConfiguration parsed = new YamlConfiguration();
        parsed.loadFromString(yaml);
        draft = new SettingsDraft(YamlTrees.toMap(parsed));
        Path file = directory.resolve("config.yml");
        Files.writeString(file, yaml);
        source = new ConfigurationSource(new ConfigFile(file,
                () -> new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8))),
                new NovaBankConfigLoader(new BukkitItemAvailability(id -> true, name -> false)), new RecordingLogger().logger());
    }

    private Setting setting(String path) {
        return SettingsCatalog.categories(draft.types()).stream().flatMap(category -> category.settings().stream())
                .filter(setting -> setting.path().equals(path)).findFirst().orElseThrow();
    }

    @Test
    void lit_le_fichier_et_revenir_a_la_valeur_d_origine_annule_le_changement() {
        assertThat(draft.number("limits.cooldown-ms")).isEqualByComparingTo("500");
        assertThat(draft.types()).containsExactly("player");

        draft.step(setting("limits.cooldown-ms"), 3);
        assertThat(draft.number("limits.cooldown-ms")).isEqualByComparingTo("800");
        assertThat(draft.dirty()).isTrue();
        draft.step(setting("limits.cooldown-ms"), -3);
        assertThat(draft.dirty()).as("retour a 500").isFalse();
    }

    @Test
    void les_nombres_restent_dans_leurs_bornes() {
        draft.step(setting("security.shutdown-drain-timeout-ms"), 1_000);
        assertThat(draft.number("security.shutdown-drain-timeout-ms")).isEqualByComparingTo("60000");
        draft.step(setting("integrations.superiorskyblock2.multiplier"), 3);
        assertThat(draft.number("integrations.superiorskyblock2.multiplier")).isEqualByComparingTo("2.5");
    }

    @Test
    void desactiver_un_type_le_retire_aussi_de_la_priorite() {
        draft.toggleType("island");
        draft.togglePriority("island");
        assertThat(draft.types()).containsExactly("player", "island");
        assertThat(draft.priority()).containsExactly("player", "island");

        draft.toggleType("island");
        assertThat(draft.priority()).containsExactly("player");
        assertThat(draft.dirty()).isFalse();
    }

    @Test
    void roles_par_defaut_puis_modifies() {
        assertThat(draft.roles("island", true)).containsExactly("owner", "admin");
        draft.toggleRole("island", true, "admin");
        draft.toggleRole("island", true, "member");
        assertThat(draft.roles("island", true)).containsExactly("owner", "member");
    }

    @Test
    void le_texte_produit_garde_les_commentaires_et_se_valide() {
        draft.toggleType("island");
        draft.set("banks.valuation-mode", "ACCUMULATED");
        draft.set("storage.sql.password", "s3cr\"et");
        draft.set("storage.sql.options", Map.of("sslMode", "verify-full"));
        draft.set("commands.player.aliases", List.of("banque", "coffre"));
        draft.step(setting("leaderboards.island.size"), 2);

        String edited = draft.applyTo(yaml);

        assertThat(draft.sections()).containsExactlyInAnyOrder("banks", "storage", "commands", "leaderboards");
        assertThat(edited).contains("entity-types: [\"player\", \"island\"]").contains("password: \"s3cr\\\"et\"")
                .contains("options: {sslMode: \"verify-full\"}").contains("aliases: [\"banque\", \"coffre\"]");
        long comments = yaml.lines().filter(line -> line.trim().startsWith("#")).count();
        assertThat(edited.lines().filter(line -> line.trim().startsWith("#")).count()).isEqualTo(comments);
        var result = source.validate(edited);
        assertThat(result.report().errors()).isEmpty();
        var config = result.config().orElseThrow();
        assertThat(config.banks().entityTypes()).containsExactly("player", "island");
        assertThat(config.leaderboards().get("island").size()).isEqualTo(20);
        assertThat(config.storage().sql().password().reveal()).isEqualTo("s3cr\"et");
    }
}
