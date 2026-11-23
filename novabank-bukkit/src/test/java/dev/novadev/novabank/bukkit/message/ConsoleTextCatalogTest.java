package dev.novadev.novabank.bukkit.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Chaque phrase de console du code a sa traduction anglaise, et le catalogue n'a pas d'entree morte. */
class ConsoleTextCatalogTest {

    private static final Pattern CALL = Pattern.compile("ConsoleText\\.tr\\(\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\d+}");

    @Test
    void toutes_les_phrases_sont_traduites_et_aucune_traduction_n_est_morte() throws IOException {
        Path root = Path.of("").toAbsolutePath().getParent();
        Set<String> used = new LinkedHashSet<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java") && p.toString().contains("/src/main/")
                    && !p.toString().contains("/target/") && !p.toString().contains("/_to_delete/")).toList()) {
                Matcher call = CALL.matcher(Files.readString(file, StandardCharsets.UTF_8));
                while (call.find()) {
                    used.add(call.group(1).replace("\\\"", "\""));
                }
            }
        }
        Set<String> translated = new TreeSet<>();
        Set<String> placeholderMismatch = new TreeSet<>();
        for (String line : Files.readAllLines(root.resolve("novabank-core/src/main/resources/console/en.tsv"))) {
            int tab = line.indexOf('\t');
            if (tab > 0 && !line.startsWith("#")) {
                String french = line.substring(0, tab);
                translated.add(french);
                if (!placeholders(french).equals(placeholders(line.substring(tab + 1)))) {
                    placeholderMismatch.add(french);
                }
            }
        }
        assertThat(used).as("phrases sans traduction anglaise").isSubsetOf(translated);
        assertThat(translated).as("traductions qui ne servent plus").isSubsetOf(used);
        assertThat(placeholderMismatch).as("parametres differents entre francais et anglais").isEmpty();
    }

    private static Set<String> placeholders(String text) {
        Set<String> found = new TreeSet<>();
        Matcher matcher = PLACEHOLDER.matcher(text);
        while (matcher.find()) {
            found.add(matcher.group());
        }
        return found;
    }
}
