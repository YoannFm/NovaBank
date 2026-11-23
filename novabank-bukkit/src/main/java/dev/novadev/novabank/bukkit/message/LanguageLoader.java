package dev.novadev.novabank.bukkit.message;

import dev.novadev.novabank.bukkit.config.ConfigFile;
import dev.novadev.novabank.bukkit.config.YamlFileUpdater;
import dev.novadev.novabank.bukkit.config.YamlTrees;
import dev.novadev.novabank.core.text.ConsoleText;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Logger;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Charge une langue : fichiers fournis copies dans {@code plugins/NovaBank/lang/} s'ils
 * sont absents, completes des cles nouvelles s'ils sont d'une version plus ancienne (valeurs de
 * l'administrateur jamais modifiees, voir {@link YamlFileUpdater}), lecture du fichier de
 * l'administrateur, repli cle par cle sur le fichier du JAR de la meme langue (ou {@value #FALLBACK_LANGUAGE} pour une langue non
 * fournie). Un fichier illisible est signale et remplace par le fichier du JAR.
 */
public final class LanguageLoader {

    /** Langues fournies dans le JAR. */
    public static final List<String> BUNDLED = List.of("fr", "en");

    /** Cle de version des fichiers de langue (absente = 0). */
    public static final String VERSION_KEY = "lang-version";

    private static final String FALLBACK_LANGUAGE = "en";
    private static final YamlFileUpdater UPDATER = new YamlFileUpdater(VERSION_KEY, Set.of(), Clock.systemUTC());
    private static final String FOLDER = "lang";

    private final Path dataFolder;
    private final Function<String, InputStream> resources;
    private final Logger logger;

    /**
     * @param dataFolder dossier du plugin
     * @param resources  ouvre une ressource du JAR ({@code null} si absente)
     * @param logger     journal
     */
    public LanguageLoader(Path dataFolder, Function<String, InputStream> resources, Logger logger) {
        this.dataFolder = Objects.requireNonNull(dataFolder, "dataFolder");
        this.resources = Objects.requireNonNull(resources, "resources");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * @param code code de langue (cle {@code language} de la configuration)
     * @return la langue chargee, jamais en echec
     */
    public Language load(String code) {
        BUNDLED.forEach(this::copyOrUpdate);
        String bundledCode = BUNDLED.contains(code) ? code : FALLBACK_LANGUAGE;
        Map<String, String> fallback = Language.flatten(readResource(bundledCode));
        Path file = file(code);
        Map<String, String> messages = fallback;
        if (Files.isRegularFile(file)) {
            try {
                messages = Language.flatten(new ConfigFile(file, () -> null).read());
            } catch (IOException | InvalidConfigurationException failure) {
                logger.warning(
                        ConsoleText.tr("Fichier de langue {0} illisible ({1}) : textes fournis avec NovaBank utilises",
                                file, failure.getMessage()));
            }
        } else {
            logger.warning(ConsoleText.tr("Fichier de langue {0} absent : textes '{1}' fournis avec NovaBank utilises",
                    file, bundledCode));
        }
        return new Language(code, messages, fallback, logger::warning);
    }

    /**
     * Copie le fichier fourni s'il est absent, sinon y ajoute les cles d'une version plus recente
     * ({@value #VERSION_KEY}), ancien fichier sauvegarde dans {@code lang/backup/}.
     */
    private void copyOrUpdate(String code) {
        ConfigFile file = new ConfigFile(file(code), () -> resources.apply(resourceName(code)));
        try {
            if (!file.createIfAbsent()) {
                file.update(UPDATER).ifPresent(result -> logger.warning(LogMessages.fileUpdated(result)));
            }
        } catch (IOException | InvalidConfigurationException failure) {
            logger.warning(ConsoleText.tr("Mise a jour du fichier de langue {0} impossible : {1}",
                    file(code), failure.getMessage()));
        }
    }

    private Map<String, Object> readResource(String code) {
        try (InputStream input = resources.apply(resourceName(code))) {
            if (input == null) {
                throw new IllegalStateException(ConsoleText.tr("Ressource {0} absente du JAR", resourceName(code)));
            }
            YamlConfiguration yaml = new YamlConfiguration();
            try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                yaml.load(reader);
            }
            return YamlTrees.toMap(yaml);
        } catch (IOException | InvalidConfigurationException failure) {
            throw new IllegalStateException(ConsoleText.tr("Ressource {0} illisible", resourceName(code)), failure);
        }
    }

    private Path file(String code) {
        return dataFolder.resolve(FOLDER).resolve(code + ".yml");
    }

    private static String resourceName(String code) {
        return FOLDER + "/" + code + ".yml";
    }
}
