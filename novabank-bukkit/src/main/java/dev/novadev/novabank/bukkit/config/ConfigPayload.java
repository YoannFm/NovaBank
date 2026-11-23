package dev.novadev.novabank.bukkit.config;

import dev.novadev.novabank.core.text.ConsoleText;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Configuration deployee entre serveurs : les sections partagees de {@code config.yml}, en texte, compressees puis codees
 * en base64url (message du canal de controle Redis). Les sections propres a un serveur ({@code server}, {@code storage},
 * {@code redis}) et la version du schema n'en font JAMAIS partie : elles portent les secrets et l'identite du serveur.
 */
public final class ConfigPayload {

    /** Sections de premier niveau qui se deploient. */
    public static final Set<String> SHARED = Set.of("language", "banks", "items", "limits", "security", "commands", "gui",
            "leaderboards", "diagnostics", "integrations");

    private static final int MAX_DECODED_BYTES = 512 * 1024;

    private ConfigPayload() {
    }

    /**
     * @param blocks texte de chaque section, par nom de section partagee
     * @return la charge utile
     * @throws IllegalArgumentException si une section n'est pas partagee ou mal formee
     */
    public static String encode(Map<String, String> blocks) {
        StringBuilder text = new StringBuilder();
        blocks.forEach((key, block) -> {
            if (!SHARED.contains(key) || !YamlSections.wellFormed(key, block)) {
                throw new IllegalArgumentException(ConsoleText.tr("Section non deployable : {0}", key));
            }
            text.append(key).append('\0').append(block).append('\0');
        });
        byte[] input = text.toString().getBytes(StandardCharsets.UTF_8);
        Deflater deflater = new Deflater();
        deflater.setInput(input);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        while (!deflater.finished()) {
            out.write(buffer, 0, deflater.deflate(buffer));
        }
        deflater.end();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray());
    }

    /**
     * @param payload charge utile recue d'un autre serveur (donnee non fiable)
     * @return les sections, ou vide si le message est invalide, trop gros, ou contient autre chose que des sections
     *         partagees bien formees
     */
    public static Optional<Map<String, String>> decode(String payload) {
        byte[] compressed;
        try {
            compressed = Base64.getUrlDecoder().decode(payload);
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }
        Inflater inflater = new Inflater();
        inflater.setInput(compressed);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        try {
            while (!inflater.finished()) {
                int read = inflater.inflate(buffer);
                if (read == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    return Optional.empty();
                }
                out.write(buffer, 0, read);
                if (out.size() > MAX_DECODED_BYTES) {
                    return Optional.empty();
                }
            }
        } catch (DataFormatException invalid) {
            return Optional.empty();
        } finally {
            inflater.end();
        }
        String[] fields = out.toString(StandardCharsets.UTF_8).split("\0", -1);
        if (fields.length < 3 || fields.length % 2 != 1 || !fields[fields.length - 1].isEmpty()) {
            return Optional.empty();
        }
        Map<String, String> blocks = new LinkedHashMap<>();
        for (int i = 0; i + 1 < fields.length; i += 2) {
            String key = fields[i];
            if (!SHARED.contains(key) || !YamlSections.wellFormed(key, fields[i + 1]) || blocks.put(key, fields[i + 1]) != null) {
                return Optional.empty();
            }
        }
        return Optional.of(blocks);
    }
}
