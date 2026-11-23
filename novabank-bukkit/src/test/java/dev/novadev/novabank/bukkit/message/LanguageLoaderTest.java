package dev.novadev.novabank.bukkit.message;

import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.bukkit.testing.RecordingLogger;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LanguageLoaderTest {

    @TempDir
    Path folder;

    private final RecordingLogger log = new RecordingLogger();

    private LanguageLoader loader() {
        return new LanguageLoader(folder, name -> LanguageLoaderTest.class.getResourceAsStream("/" + name),
                log.logger());
    }

    @Test
    void les_fichiers_fournis_sont_copies_s_ils_sont_absents_puis_jamais_ecrases() throws IOException {
        Language french = loader().load("fr");

        assertThat(folder.resolve("lang/fr.yml")).exists();
        assertThat(folder.resolve("lang/en.yml")).exists();
        assertThat(french.text("general.players-only")).contains("reservee aux joueurs");

        Files.writeString(folder.resolve("lang/fr.yml"), "prefix: \"[Banque] \"\ngeneral:\n  players-only: \"Joueurs seulement\"\n",
                StandardCharsets.UTF_8);
        Language customized = loader().load("fr");

        assertThat(customized.chat("general.players-only")).isEqualTo("[Banque] Joueurs seulement");
        assertThat(customized.text("general.no-permission")).contains("pas la permission");
        assertThat(log.messages()).anySatisfy(line -> assertThat(line).contains("general.no-permission"));
        assertThat(Files.readString(folder.resolve("lang/fr.yml"))).contains("Joueurs seulement");
    }

    @Test
    void fichier_illisible_textes_du_jar_utilises() throws IOException {
        Files.createDirectories(folder.resolve("lang"));
        Files.writeString(folder.resolve("lang/en.yml"), "general: [non ferme", StandardCharsets.UTF_8);

        Language english = loader().load("en");

        assertThat(english.text("general.players-only")).contains("only be used by players");
        assertThat(log.messages()).anySatisfy(line -> assertThat(line).contains("illisible"));
    }

    @Test
    void langue_non_fournie_sans_fichier_repli_sur_l_anglais() {
        Language german = loader().load("de");

        assertThat(german.code()).isEqualTo("de");
        assertThat(german.text("general.players-only")).contains("only be used by players");
        assertThat(log.messages()).anySatisfy(line -> assertThat(line).contains("lang/de.yml", "absent"));
    }

    @Test
    void un_ancien_fichier_sans_les_cles_gui_est_complete_sauvegarde_et_garde_les_textes_de_l_admin() throws Exception {
        Path lang = Files.createDirectories(folder.resolve("lang"));
        String old;
        try (InputStream input = LanguageLoaderTest.class.getResourceAsStream("/anciens/fr-avant-gui.yml")) {
            old = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        String customized = old.replace("Vous n'avez pas la permission", "Permission refusee, desole")
                + "obsolete:\n  ancienne-cle: \"plus utilisee\"\n";
        Files.writeString(lang.resolve("fr.yml"), customized, StandardCharsets.UTF_8);

        Language french = loader().load("fr");

        String merged = Files.readString(lang.resolve("fr.yml"), StandardCharsets.UTF_8);
        assertThat(merged).contains("lang-version: 10").contains("Permission refusee, desole").contains("title:")
                .contains("plus utilisee");
        assertThat(french.text("gui.title", "bank", "NovaBot")).contains("Banque : NovaBot");
        assertThat(french.text("general.no-permission")).contains("Permission refusee, desole");
        try (var backups = Files.list(lang.resolve("backup"))) {
            Path backup = backups.findFirst().orElseThrow();
            assertThat(backup.getFileName().toString()).startsWith("fr.yml.");
            assertThat(Files.readString(backup, StandardCharsets.UTF_8)).isEqualTo(customized);
        }
        assertThat(log.messages()).anySatisfy(line -> assertThat(line).contains("mis a jour (version 0 -> 10)")
                .contains("gui.title").contains("obsolete").contains("obsolete.ancienne-cle"));
        assertThat(log.messages()).noneSatisfy(line -> assertThat(line).contains("Cle de langue absente"));

        loader().load("fr");
        try (var backups = Files.list(lang.resolve("backup"))) {
            assertThat(backups.count()).as("a jour : aucune nouvelle sauvegarde").isEqualTo(1);
        }
    }
}
