package dev.novadev.novabank.bukkit.config;

import dev.novadev.novabank.config.ConfigLoadResult;
import dev.novadev.novabank.core.text.ConsoleText;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Application d'un changement de configuration, fait en jeu ou recu d'un autre serveur : les sections sont remplacees
 * dans le texte de {@code config.yml}, la configuration obtenue est VALIDEE EN ENTIER, puis le fichier est sauvegarde
 * dans {@code backup/} et reecrit. Rien n'est ecrit si la validation echoue. A appeler hors du thread principal.
 */
public final class ConfigDeployer {

    /**
     * @param applied vrai si le fichier correspond maintenant au changement demande
     * @param changed vrai si le fichier a ete reecrit (faux : deja identique, ou refuse)
     * @param errors  raisons du refus, vides si accepte
     */
    public record Outcome(boolean applied, boolean changed, List<String> errors) {
    }

    private final Path file;
    private final ConfigWriter writer;
    private final Function<String, ConfigLoadResult> validator;

    /**
     * @param file      {@code config.yml}
     * @param writer    ecriture avec sauvegarde
     * @param validator validation d'un contenu complet
     */
    public ConfigDeployer(Path file, ConfigWriter writer, Function<String, ConfigLoadResult> validator) {
        this.file = Objects.requireNonNull(file, "file");
        this.writer = Objects.requireNonNull(writer, "writer");
        this.validator = Objects.requireNonNull(validator, "validator");
    }

    /**
     * @param blocks sections a remplacer (sections partagees uniquement), texte complet de chaque section
     * @param reason motif court de la sauvegarde ({@code gui}, {@code items}, {@code remote}...)
     * @return le resultat
     */
    public synchronized Outcome apply(Map<String, String> blocks, String reason) {
        try {
            String current = Files.readString(file, StandardCharsets.UTF_8);
            String proposed = current;
            for (Map.Entry<String, String> block : blocks.entrySet()) {
                if (!ConfigPayload.SHARED.contains(block.getKey()) || !YamlSections.wellFormed(block.getKey(), block.getValue())) {
                    return refused(ConsoleText.tr("section non deployable : {0}", block.getKey()));
                }
                proposed = YamlSections.replace(proposed, block.getKey(), block.getValue());
            }
            return writeIfValid(current, proposed, reason);
        } catch (IOException failure) {
            return refused(ConsoleText.tr("fichier illisible ou non ecrit ({0})", failure.getClass().getSimpleName()));
        }
    }

    /**
     * Modification faite sur CE serveur, par transformation du texte du fichier : toutes les sections sont permises, y
     * compris {@code server}, {@code storage} et {@code redis} (jamais diffusees : voir {@link #payloadFor}). Meme
     * validation complete et meme sauvegarde que {@link #apply}.
     *
     * @param change transformation du texte actuel du fichier
     * @param reason motif court de la sauvegarde
     * @return le resultat
     */
    public synchronized Outcome edit(UnaryOperator<String> change, String reason) {
        try {
            String current = Files.readString(file, StandardCharsets.UTF_8);
            return writeIfValid(current, change.apply(current), reason);
        } catch (IOException failure) {
            return refused(ConsoleText.tr("fichier illisible ou non ecrit ({0})", failure.getClass().getSimpleName()));
        } catch (IllegalArgumentException invalid) {
            return refused(invalid.getMessage());
        }
    }

    private Outcome writeIfValid(String current, String proposed, String reason) throws IOException {
        if (proposed.equals(current)) {
            return new Outcome(true, false, List.of());
        }
        ConfigLoadResult result = validator.apply(proposed);
        if (result.config().isEmpty()) {
            List<String> errors = new ArrayList<>();
            result.report().errors().forEach(error -> errors.add(error.path() + " : " + error.message()));
            return new Outcome(false, false, errors);
        }
        writer.write(proposed, reason);
        return new Outcome(true, true, List.of());
    }

    /**
     * Applique une configuration recue d'un autre serveur.
     *
     * @param payload charge utile du canal de controle
     * @return le resultat ; refuse si le message est invalide
     */
    public Outcome receive(String payload) {
        Optional<Map<String, String>> blocks = ConfigPayload.decode(payload);
        return blocks.isEmpty() ? refused(
                ConsoleText.tr("message de configuration invalide")) : apply(blocks.get(), "remote");
    }

    /**
     * @param keys sections a envoyer aux autres serveurs
     * @return la charge utile construite depuis le fichier actuel, vide si aucune section n'existe
     */
    public synchronized Optional<String> payloadFor(Collection<String> keys) {
        try {
            String current = Files.readString(file, StandardCharsets.UTF_8);
            Map<String, String> blocks = new LinkedHashMap<>();
            for (String key : keys) {
                if (ConfigPayload.SHARED.contains(key)) {
                    YamlSections.extract(current, key).ifPresent(block -> blocks.put(key, block));
                }
            }
            return blocks.isEmpty() ? Optional.empty() : Optional.of(ConfigPayload.encode(blocks));
        } catch (IOException failure) {
            return Optional.empty();
        }
    }

    private static Outcome refused(String reason) {
        return new Outcome(false, false, List.of(reason));
    }
}
