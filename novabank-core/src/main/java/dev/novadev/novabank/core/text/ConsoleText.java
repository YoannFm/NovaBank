package dev.novadev.novabank.core.text;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Langue des messages de la console et des erreurs de configuration. Le texte francais sert de cle (comme gettext) :
 * chaque appel garde sa phrase lisible dans le code, et {@code console/en.tsv} donne la traduction anglaise
 * ({@code francais<TAB>anglais}, une par ligne). Parametres : {@code {0}}, {@code {1}}... remplaces tels quels (pas de
 * {@code MessageFormat} : les apostrophes francaises restent intactes). Un texte sans traduction reste en francais.
 *
 * <p>Etat global volontaire : la console est unique par serveur, et chaque JAR NovaBank lit la meme langue
 * ({@code language} de {@code plugins/NovaBank/config.yml}).
 */
public final class ConsoleText {

    static final String RESOURCE = "/console/en.tsv";

    private static volatile boolean english;
    private static volatile Map<String, String> translations;

    private ConsoleText() {
    }

    /**
     * @param language code de langue de la configuration ({@code fr} : francais ; toute autre langue : anglais)
     */
    public static void language(String language) {
        english = language != null && !language.trim().equalsIgnoreCase("fr");
    }

    /**
     * @param french    texte francais, avec {@code {0}}, {@code {1}}... pour les parametres
     * @param arguments parametres
     * @return le texte dans la langue de la console, parametres remplaces
     */
    public static String tr(String french, Object... arguments) {
        String text = english ? translations().getOrDefault(french, french) : french;
        for (int i = 0; i < arguments.length; i++) {
            text = text.replace("{" + i + "}", String.valueOf(arguments[i]));
        }
        return text;
    }

    /** @return les traductions anglaises, lues une fois */
    static Map<String, String> translations() {
        Map<String, String> loaded = translations;
        if (loaded == null) {
            loaded = read();
            translations = loaded;
        }
        return loaded;
    }

    private static Map<String, String> read() {
        Map<String, String> map = new HashMap<>();
        try (InputStream in = ConsoleText.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                return Map.of();
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                int tab = line.indexOf('\t');
                if (tab > 0 && !line.startsWith("#")) {
                    map.put(line.substring(0, tab), line.substring(tab + 1));
                }
            }
        } catch (IOException unreadable) {
            return Map.of();
        }
        return Map.copyOf(map);
    }
}
