package dev.novadev.novabank.config;

import dev.novadev.novabank.config.migration.ConfigMigrator;
import dev.novadev.novabank.config.tree.ConfigTrees;
import dev.novadev.novabank.config.tree.SecretPaths;
import dev.novadev.novabank.config.validation.ValidationReport;
import dev.novadev.novabank.core.text.ConsoleText;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Modification de la configuration en jeu, sans Bukkit : applique {@code chemin -> valeur}
 * sur l'arbre courant, revalide toute la configuration et n'accepte le resultat que s'il
 * ne contient aucune erreur.
 *
 * <p>Refus systematiques : chemin secret ou section contenant un secret (D5, a modifier
 * dans le fichier uniquement), {@code config-version}, chemin mal forme ou inconnu des
 * parseurs. La valeur peut etre du texte brut saisi en jeu ({@code "25"}, {@code "true"}) :
 * la conversion est faite par la validation. Une valeur {@code null} retire la cle
 * (retour a la valeur par defaut). Sans etat : l'appelant conserve l'etat courant.
 */
public final class ConfigEditor {

    private static final Pattern PATH = Pattern.compile("[A-Za-z0-9_-]+(\\.[A-Za-z0-9_-]+)*");

    private final NovaBankConfigLoader loader;

    /**
     * Cree un editeur.
     *
     * @param loader chargeur utilise pour revalider apres modification
     */
    public ConfigEditor(NovaBankConfigLoader loader) {
        this.loader = Objects.requireNonNull(loader, "loader");
    }

    /**
     * Tente une modification.
     *
     * @param current etat courant
     * @param path    chemin modifie, par exemple {@code limits.cooldown-ms}
     * @param value   nouvelle valeur, ou {@code null} pour revenir a la valeur par defaut
     * @return le resultat : nouvel etat si accepte, sinon etat d'origine et raison du refus
     */
    public EditOutcome edit(ConfigLoadResult current, String path, Object value) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(path, "path");
        if (!PATH.matcher(path).matches()) {
            return refuse(current, path,
                    ConsoleText.tr("chemin invalide (attendu : cles separees par des points, ex. limits.cooldown-ms)"));
        }
        if (SecretPaths.touchesSecret(path)) {
            return refuse(current, path,
                    ConsoleText.tr("valeur secrete : modifiable uniquement dans config.yml, jamais en jeu"));
        }
        if (path.equals(ConfigMigrator.VERSION_KEY)) {
            return refuse(current, path, ConsoleText.tr("gere par NovaBank, non modifiable"));
        }
        Optional<Map<String, Object>> edited = ConfigTrees.withValue(current.tree(), path, value);
        if (edited.isEmpty()) {
            return refuse(current, path, ConsoleText.tr("un niveau de ce chemin n'est pas une section"));
        }
        NovaBankConfigLoader.Evaluation evaluation = loader.evaluate(edited.get());
        if (!evaluation.context().isRecognized(path)) {
            return refuse(current, path, ConsoleText.tr("chemin inconnu"));
        }
        ConfigLoadResult result = evaluation.result();
        if (result.config().isEmpty()) {
            return new EditOutcome(false, current, result.report());
        }
        return new EditOutcome(true, result, result.report());
    }

    private static EditOutcome refuse(ConfigLoadResult current, String path, String reason) {
        return new EditOutcome(false, current, ValidationReport.ofError(path, reason));
    }
}
