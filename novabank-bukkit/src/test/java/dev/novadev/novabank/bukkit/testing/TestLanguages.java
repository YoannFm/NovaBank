package dev.novadev.novabank.bukkit.testing;

import dev.novadev.novabank.bukkit.config.YamlTrees;
import dev.novadev.novabank.bukkit.message.Language;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Langues fournies dans le JAR, chargees depuis le classpath de test.
 */
public final class TestLanguages {

    /** Avertissements emis par les langues creees ici. */
    public static final List<String> WARNINGS = new CopyOnWriteArrayList<>();

    private TestLanguages() {
    }

    /**
     * @return la langue francaise fournie
     */
    public static Language french() {
        return bundled("fr");
    }

    /**
     * @param code langue fournie
     * @return la langue, sans fichier d'administrateur (fichier du JAR seul)
     */
    public static Language bundled(String code) {
        Map<String, String> texts = texts(code);
        return new Language(code, texts, texts, WARNINGS::add);
    }

    /**
     * @param code langue fournie
     * @return ses textes, cles a points
     */
    public static Map<String, String> texts(String code) {
        try (InputStream input = TestLanguages.class.getResourceAsStream("/lang/" + code + ".yml")) {
            if (input == null) {
                throw new IllegalStateException("lang/" + code + ".yml absent du classpath");
            }
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(new InputStreamReader(input, StandardCharsets.UTF_8));
            return Language.flatten(YamlTrees.toMap(yaml));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        } catch (InvalidConfigurationException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
