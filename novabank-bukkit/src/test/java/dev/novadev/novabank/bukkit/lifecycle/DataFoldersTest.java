package dev.novadev.novabank.bukkit.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DataFoldersTest {

    @TempDir
    Path plugins;

    @Test
    void premier_demarrage_le_dossier_du_jar_qui_demarre_le_moteur() {
        Path module = plugins.resolve("SuperiorSkyblock2/modules/NovaBank");

        assertThat(DataFolders.locate(plugins, module)).isEqualTo(module);
    }

    @Test
    void un_dossier_deja_configure_sert_a_tous_les_jar() throws Exception {
        Path module = config("SuperiorSkyblock2/modules/NovaBank");

        assertThat(DataFolders.locate(plugins, plugins.resolve("NovaBank"))).isEqualTo(module);
        assertThat(DataFolders.locate(plugins, plugins.resolve("BentoBox/addons/NovaBank"))).isEqualTo(module);
    }

    @Test
    void une_installation_dans_plugins_novabank_est_reprise_en_priorite() throws Exception {
        Path standalone = config("NovaBank");
        config("BentoBox/addons/NovaBank");

        assertThat(DataFolders.locate(plugins, plugins.resolve("BentoBox/addons/NovaBank"))).isEqualTo(standalone);
    }

    @Test
    void un_dossier_sans_config_yml_ne_compte_pas() throws Exception {
        Files.createDirectories(plugins.resolve("NovaBank/libs"));
        Path addon = plugins.resolve("BentoBox/addons/NovaBank");

        assertThat(DataFolders.locate(plugins, addon)).isEqualTo(addon);
    }

    private Path config(String folder) throws Exception {
        Path path = Files.createDirectories(plugins.resolve(folder));
        Files.writeString(path.resolve("config.yml"), "language: fr\n");
        return path;
    }
}
