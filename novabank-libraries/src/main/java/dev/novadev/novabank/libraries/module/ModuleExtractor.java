package dev.novadev.novabank.libraries.module;

import dev.novadev.novabank.core.text.ConsoleText;
import dev.novadev.novabank.libraries.LibraryException;
import dev.novadev.novabank.libraries.LibraryLogger;
import dev.novadev.novabank.libraries.artifact.ArtifactChecksums;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrait le jar d'un module embarque du jar du plugin vers le dossier des bibliotheques.
 *
 * <p>La somme attendue est lue dans la ressource generee au build. Une copie deja extraite et
 * intacte est reutilisee ; sinon le jar est ecrit dans un fichier temporaire, verifie pendant la
 * copie, deplace atomiquement, puis le fichier final est verifie a nouveau avant d'etre rendu.
 * Protege contre une ressource tronquee et contre une copie extraite modifiee ou corrompue ;
 * l'integrite du jar du plugin lui-meme releve de sa signature.
 */
public final class ModuleExtractor {

    private static final Pattern SHA256 = Pattern.compile("^\\s*([0-9a-fA-F]{64})\\b");

    private final Path directory;
    private final ClassLoader resources;
    private final LibraryLogger logger;

    /**
     * @param directory dossier des bibliotheques ({@code plugins/NovaBank/libs})
     * @param resources chargeur qui voit les ressources du jar du plugin
     * @param logger    journal
     */
    public ModuleExtractor(Path directory, ClassLoader resources, LibraryLogger logger) {
        this.directory = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
        this.resources = Objects.requireNonNull(resources, "resources");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * @param module module a extraire
     * @return le jar extrait et verifie
     * @throws LibraryException si la ressource ou sa somme manque, si le contenu ne correspond pas
     *                          a la somme, ou si le dossier n'est pas inscriptible
     */
    public synchronized Path extract(EmbeddedModule module) {
        byte[] expected = expectedDigest(module);
        Path target = directory.resolve(module.resource()).normalize();
        try {
            if (Files.isRegularFile(target) && MessageDigest.isEqual(expected, ArtifactChecksums.sha256(target))) {
                return target;
            }
            if (Files.exists(target)) {
                // Cas normal apres une mise a jour du plugin ; sinon copie modifiee ou corrompue.
                logger.info(
                        ConsoleText.tr("Module {0} : copie extraite differente du jar du plugin, extraction a nouveau",
                                module.id()));
            }
            Files.createDirectories(target.getParent());
            Path part = Files.createTempFile(target.getParent(), target.getFileName() + ".", ".part");
            try {
                copyVerified(module, expected, part);
                moveIntoPlace(part, target);
            } finally {
                // Sans effet apres un deplacement reussi ; supprime le fichier partiel sinon.
                Files.deleteIfExists(part);
            }
            if (!MessageDigest.isEqual(expected, ArtifactChecksums.sha256(target))) {
                throw new LibraryException(ConsoleText.tr("Module {0} : somme SHA-256 invalide apres extraction ({1})",
                        module.id(), target));
            }
            logger.info(ConsoleText.tr("Module {0} extrait : {1} (sha256 {2}...)",
                    module.id(), directory.relativize(target), ArtifactChecksums.hex(expected).substring(0, 12)));
            return target;
        } catch (IOException failure) {
            throw new LibraryException(ConsoleText.tr("Extraction du module {0} impossible ({1}) : {2}",
                    module.id(), target, failure), failure);
        }
    }

    private byte[] expectedDigest(EmbeddedModule module) {
        try (InputStream in = resources.getResourceAsStream(module.checksumResource())) {
            if (in == null) {
                throw new LibraryException(ConsoleText.tr("Module {0} : somme {1} absente du jar du plugin",
                        module.id(), module.checksumResource()));
            }
            Matcher matcher = SHA256.matcher(new String(in.readNBytes(256), StandardCharsets.US_ASCII));
            if (!matcher.find()) {
                throw new LibraryException(ConsoleText.tr("Module {0} : somme {1} illisible",
                        module.id(), module.checksumResource()));
            }
            return HexFormat.of().parseHex(matcher.group(1).toLowerCase(java.util.Locale.ROOT));
        } catch (IOException failure) {
            throw new LibraryException(ConsoleText.tr("Module {0} : lecture de la somme impossible",
                    module.id()), failure);
        }
    }

    private void copyVerified(EmbeddedModule module, byte[] expected, Path part) throws IOException {
        MessageDigest digest = ArtifactChecksums.newDigest();
        try (InputStream in = resources.getResourceAsStream(module.resource())) {
            if (in == null) {
                throw new LibraryException(ConsoleText.tr("Module {0} : ressource {1} absente du jar du plugin",
                        module.id(), module.resource()));
            }
            Files.copy(new DigestInputStream(in, digest), part, StandardCopyOption.REPLACE_EXISTING);
        }
        byte[] actual = digest.digest();
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new LibraryException(
                    ConsoleText.tr("Module {0} : la ressource {1} ne correspond pas a sa somme (recue {2})",
                            module.id(), module.resource(), ArtifactChecksums.hex(actual)));
        }
    }

    private static void moveIntoPlace(Path part, Path target) throws IOException {
        try {
            Files.move(part, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
