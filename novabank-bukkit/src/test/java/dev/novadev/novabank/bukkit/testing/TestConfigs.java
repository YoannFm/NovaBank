package dev.novadev.novabank.bukkit.testing;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Textes de configuration de test, derives du config.yml par defaut du module config.
 */
public final class TestConfigs {

    private TestConfigs() {
    }

    /**
     * @return le config.yml par defaut, tel qu'embarque dans le JAR (valide tel quel)
     */
    public static String defaultYaml() {
        try (InputStream input = TestConfigs.class.getResourceAsStream("/config.yml")) {
            if (input == null) {
                throw new IllegalStateException("config.yml par defaut absent du classpath");
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /**
     * @param yaml   texte
     * @param target fragment present une seule fois
     * @param value  remplacement
     * @return texte modifie
     */
    public static String replaceOnce(String yaml, String target, String value) {
        int index = yaml.indexOf(target);
        if (index < 0 || yaml.indexOf(target, index + 1) >= 0) {
            throw new IllegalArgumentException("Fragment absent ou multiple : " + target);
        }
        return yaml.substring(0, index) + value + yaml.substring(index + target.length());
    }
}
