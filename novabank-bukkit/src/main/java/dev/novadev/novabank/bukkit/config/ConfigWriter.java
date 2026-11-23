package dev.novadev.novabank.bukkit.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Objects;

/**
 * Ecriture de {@code config.yml} : le fichier actuel est TOUJOURS copie dans {@code backup/} (horodate) avant qu'une valeur
 * change, puis le nouveau contenu est ecrit par remplacement atomique.
 */
public final class ConfigWriter {

    private final Path file;

    /**
     * @param file fichier {@code config.yml}
     */
    public ConfigWriter(Path file) {
        this.file = Objects.requireNonNull(file, "file");
    }

    /**
     * @param yaml   nouveau contenu
     * @param reason motif court ajoute au nom de la sauvegarde (lettres, chiffres, '-')
     * @return la sauvegarde de l'ancien fichier
     * @throws IOException si la sauvegarde ou l'ecriture echoue (le fichier n'est alors pas modifie)
     */
    public Path write(String yaml, String reason) throws IOException {
        Path backups = file.toAbsolutePath().getParent().resolve("backup");
        Files.createDirectories(backups);
        Path backup = backups.resolve("config-" + Instant.now().toString().replace(':', '-') + "-" + reason + ".yml");
        Files.copy(file, backup, StandardCopyOption.COPY_ATTRIBUTES);
        Path temporary = file.resolveSibling(file.getFileName() + ".edit");
        Files.writeString(temporary, yaml, StandardCharsets.UTF_8);
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        return backup;
    }
}
