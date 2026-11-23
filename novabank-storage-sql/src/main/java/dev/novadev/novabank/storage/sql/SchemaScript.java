package dev.novadev.novabank.storage.sql;

import dev.novadev.novabank.core.text.ConsoleText;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Script de migration {@code schema/<dialecte>/V<n>.sql} : instructions separees par
 * {@code ;} (donc sans {@code ;} dans un litteral), lignes de commentaire {@code --},
 * prefixe des tables ecrit {@code ${prefix}}.
 */
final class SchemaScript {

    private static final String PREFIX_PLACEHOLDER = "${prefix}";

    private SchemaScript() {
    }

    /**
     * @param dialectName dossier du dialecte
     * @param version     version de schema
     * @param prefix      prefixe des tables, deja valide
     * @return les instructions du script, dans l'ordre
     * @throws IllegalStateException si le script n'existe pas
     */
    static List<String> statements(String dialectName, int version, String prefix) {
        // Chemin absolu : il reste valable meme si la classe change de paquet.
        String resource = "/dev/novadev/novabank/storage/sql/schema/" + dialectName + "/V" + version + ".sql";
        try (InputStream input = SchemaScript.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException(ConsoleText.tr("Script de migration absent : {0}", resource));
            }
            return split(new String(input.readAllBytes(), StandardCharsets.UTF_8).replace(PREFIX_PLACEHOLDER, prefix));
        } catch (IOException failure) {
            throw new UncheckedIOException(ConsoleText.tr("Lecture impossible du script {0}", resource), failure);
        }
    }

    private static List<String> split(String script) {
        StringBuilder withoutComments = new StringBuilder();
        for (String line : script.split("\n", -1)) {
            if (!line.strip().startsWith("--")) {
                withoutComments.append(line).append('\n');
            }
        }
        List<String> statements = new ArrayList<>();
        for (String statement : withoutComments.toString().split(";")) {
            if (!statement.isBlank()) {
                statements.add(statement.strip());
            }
        }
        return statements;
    }
}
