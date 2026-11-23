package dev.novadev.novabank.bukkit.config;

import dev.novadev.novabank.core.text.ConsoleText;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Fichier YAML du plugin ({@code config.yml}, fichiers de langue) : copie du fichier par
 * defaut s'il est absent, lecture YAML par Bukkit.
 */
public final class ConfigFile {

    /** Nom du fichier et de la ressource par defaut embarquee dans le JAR. */
    public static final String NAME = "config.yml";

    /** Fichier fourni commente en anglais, pour {@code language} autre que {@code fr}. */
    public static final String ENGLISH = "config-en.yml";

    private static final String LANGUAGE_KEY = "language";

    private final Path file;
    private final Supplier<InputStream> defaults;
    private final Supplier<InputStream> english;

    /**
     * @param file     chemin du fichier
     * @param defaults ouvre la ressource par defaut ({@code null} si absente du JAR)
     */
    public ConfigFile(Path file, Supplier<InputStream> defaults) {
        this(file, defaults, defaults);
    }

    /**
     * @param file     chemin du fichier
     * @param defaults ouvre la ressource par defaut, commentee en francais ({@code null} si absente du JAR)
     * @param english  ouvre la meme ressource commentee en anglais
     */
    public ConfigFile(Path file, Supplier<InputStream> defaults, Supplier<InputStream> english) {
        this.file = Objects.requireNonNull(file, "file");
        this.defaults = Objects.requireNonNull(defaults, "defaults");
        this.english = Objects.requireNonNull(english, "english");
    }

    /**
     * @return chemin du fichier
     */
    public Path path() {
        return file;
    }

    /**
     * Copie le fichier par defaut si le fichier n'existe pas ; un fichier existant n'est
     * jamais modifie.
     *
     * @return {@code true} si le fichier vient d'etre cree
     * @throws IOException si la copie echoue ou si la ressource par defaut est absente
     */
    public boolean createIfAbsent() throws IOException {
        if (Files.exists(file)) {
            return false;
        }
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (InputStream input = defaults.get()) {
            if (input == null) {
                throw new IOException(ConsoleText.tr("Ressource {0} absente du JAR", file.getFileName()));
            }
            Files.copy(input, file);
            return true;
        } catch (FileAlreadyExistsException concurrentCreation) {
            return false;
        }
    }

    /**
     * Met le fichier a jour depuis le fichier fourni s'il est d'une version plus ancienne.
     *
     * @param updater regles de mise a jour (cle de version, sections libres)
     * @return le bilan si le fichier a ete mis a jour
     * @throws IOException                   lecture ou ecriture impossible (fichier inchange)
     * @throws InvalidConfigurationException YAML invalide (fichier inchange)
     */
    public Optional<YamlFileUpdater.Result> update(YamlFileUpdater updater)
            throws IOException, InvalidConfigurationException {
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        return updater.update(file, french() ? defaults : english);
    }

    /**
     * Met les commentaires du fichier dans la langue de sa cle {@code language} s'ils sont encore ceux fournis dans
     * l'autre langue (valeurs inchangees, ancien fichier dans {@code backup/}).
     *
     * @param updater regles d'ecriture (sauvegarde)
     * @return la sauvegarde si le fichier a ete reecrit
     * @throws IOException                   lecture ou ecriture impossible (fichier inchange)
     * @throws InvalidConfigurationException YAML invalide (fichier inchange)
     */
    public Optional<Path> localizeComments(YamlFileUpdater updater) throws IOException, InvalidConfigurationException {
        // Meme ressource pour les deux langues (constructeur a deux arguments, fichiers de langue) : rien a basculer.
        if (defaults == english || !Files.isRegularFile(file)) {
            return Optional.empty();
        }
        boolean french = french();
        return updater.recomment(file, french ? defaults : english, french ? english : defaults, LANGUAGE_KEY);
    }

    /** Meme regle que la console : tout autre code que {@code fr} donne l'anglais. */
    private boolean french() throws IOException, InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(file.toFile());
        return "fr".equals(yaml.getString(LANGUAGE_KEY, "fr"));
    }

    /**
     * Lit le fichier.
     *
     * @return l'arbre generique du fichier
     * @throws IOException                   si le fichier est illisible
     * @throws InvalidConfigurationException si le YAML est invalide
     */
    public Map<String, Object> read() throws IOException, InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(file.toFile());
        return YamlTrees.toMap(yaml);
    }
}
