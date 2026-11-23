package dev.novadev.novabank.bukkit.config;

import dev.novadev.novabank.bukkit.message.LogMessages;
import dev.novadev.novabank.config.ConfigLoadResult;
import dev.novadev.novabank.config.NovaBankConfigLoader;
import dev.novadev.novabank.config.migration.ConfigMigrator;
import dev.novadev.novabank.config.validation.ValidationReport;
import dev.novadev.novabank.core.text.ConsoleText;
import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.configuration.InvalidConfigurationException;

/**
 * Produit un {@link ConfigLoadResult} depuis {@code config.yml} : creation du fichier par
 * defaut si absent, lecture YAML, puis migration et validation par
 * {@link NovaBankConfigLoader}. Un fichier illisible devient une ERREUR du rapport, jamais
 * une exception.
 */
public final class ConfigurationSource {

    /**
     * Sections dont les cles sont choisies par l'administrateur : rien n'y est ajoute si elles
     * existent (les exemples du fichier fourni ne doivent pas reapparaitre). Les cles retirees
     * par les migrations de schema sont aussi retirees du fichier, pour ne pas etre signalees
     * comme inconnues a chaque demarrage.
     */
    private static final YamlFileUpdater UPDATER = new YamlFileUpdater(ConfigMigrator.VERSION_KEY,
            Set.of("banks.roles", "leaderboards", "storage.sql.options", "items", "gui.buttons"),
            ConfigMigrator.standard()::removedKeysSince, Clock.systemUTC());

    private static final Pattern YAML_POSITION = Pattern.compile("line (\\d+), column (\\d+)");

    private final ConfigFile file;
    private final NovaBankConfigLoader loader;
    private final Logger logger;

    /**
     * @param file   fichier de configuration
     * @param loader chargeur et validateur
     * @param logger journal (creation du fichier par defaut)
     */
    public ConfigurationSource(ConfigFile file, NovaBankConfigLoader loader, Logger logger) {
        this.file = Objects.requireNonNull(file, "file");
        this.loader = Objects.requireNonNull(loader, "loader");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * @return le resultat du chargement ; configuration absente si le rapport contient une erreur
     */
    public ConfigLoadResult load() {
        Map<String, Object> tree;
        try {
            if (file.createIfAbsent()) {
                logger.info(LogMessages.defaultConfigCreated(file.path()));
            }
            updateFile();
            tree = file.read();
        } catch (IOException failure) {
            return unreadable(LogMessages.configUnreadable(String.valueOf(failure.getMessage())));
        } catch (InvalidConfigurationException failure) {
            // Le message SnakeYAML cite la ligne fautive, qui peut contenir un secret :
            // seule la position est reprise.
            return unreadable(LogMessages.configInvalidYaml(yamlPosition(failure)));
        }
        return loader.load(tree);
    }

    /**
     * Valide un contenu de {@code config.yml} sans toucher au fichier (modification en jeu, configuration recue d'un autre
     * serveur).
     *
     * @param yaml contenu complet propose
     * @return le resultat de la validation ; configuration absente si le texte contient une erreur
     */
    public ConfigLoadResult validate(String yaml) {
        org.bukkit.configuration.file.YamlConfiguration parsed = new org.bukkit.configuration.file.YamlConfiguration();
        try {
            parsed.loadFromString(yaml);
        } catch (InvalidConfigurationException failure) {
            return unreadable(LogMessages.configInvalidYaml(yamlPosition(failure)));
        }
        return loader.load(YamlTrees.toMap(parsed));
    }

    /**
     * Ajoute au fichier les cles d'une version plus recente de NovaBank et retire celles qu'elle a
     * supprimees (sauvegarde dans {@code backup/}). Un fichier illisible n'est pas touche : l'erreur
     * est signalee par la lecture.
     */
    private void updateFile() {
        try {
            file.update(UPDATER).ifPresent(result -> logger.warning(LogMessages.fileUpdated(result)));
            file.localizeComments(UPDATER).ifPresent(backup -> logger.info(ConsoleText.tr(
                    "Commentaires de config.yml mis dans la langue de language (ancien fichier : {0})", backup)));
        } catch (IOException | InvalidConfigurationException notUpdated) {
            logger.fine(ConsoleText.tr("Mise a jour de {0} non faite : {1}", file.path(), notUpdated.getMessage()));
        }
    }

    private static ConfigLoadResult unreadable(String message) {
        return new ConfigLoadResult(Optional.empty(), ValidationReport.ofError(ConfigFile.NAME, message), Map.of(),
                false);
    }

    private static Optional<String> yamlPosition(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            Matcher matcher = YAML_POSITION.matcher(String.valueOf(cause.getMessage()));
            if (matcher.find()) {
                return Optional.of(matcher.group(1) + ":" + matcher.group(2));
            }
        }
        return Optional.empty();
    }
}
