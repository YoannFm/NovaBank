package dev.novadev.novabank.bukkit.message;

import dev.novadev.novabank.bukkit.config.YamlFileUpdater;
import dev.novadev.novabank.bukkit.platform.MinecraftVersion;
import dev.novadev.novabank.bukkit.platform.ServerEnvironment;
import dev.novadev.novabank.config.model.StorageType;
import dev.novadev.novabank.core.text.ConsoleText;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Textes du journal du serveur (demarrage, arret, configuration, stockage), en francais.
 * Les textes destines aux joueurs et aux administrateurs en jeu sont dans les fichiers de
 * langue ({@link Language}). Aucun texte ne contient de valeur secrete.
 */
public final class LogMessages {

    private LogMessages() {
    }

    // ---- Demarrage et arret (journal) ----

    /**
     * @param environment environnement detecte
     * @return ligne de journal
     */
    public static String environmentDetected(ServerEnvironment environment) {
        return ConsoleText.tr("Environnement : Minecraft {0}, {1} ({2}), Java {3}",
                environment.versionLabel(), environment.platform().displayName(), environment.serverName(), environment.javaVersion());
    }

    /**
     * @param actual  version constatee
     * @param minimum version minimale de Folia
     * @return raison du refus sous Folia
     */
    public static String foliaVersionTooOld(MinecraftVersion actual, MinecraftVersion minimum) {
        return ConsoleText.tr("Folia {0} n'est pas supporte : NovaBank exige Folia {1} ou plus recent. NovaBank ne demarre pas.",
                actual, minimum);
    }

    /**
     * @param environment environnement constate
     * @param cause       cause technique
     * @return raison du refus quand le planificateur de la plateforme ne peut pas etre cree
     */
    public static String schedulerUnavailable(ServerEnvironment environment, String cause) {
        return ConsoleText.tr("Planificateur {0} indisponible, JAR NovaBank incomplet ? {1}. NovaBank ne demarre pas.",
                environment.platform().displayName(), cause);
    }

    /**
     * @param actual  version du serveur
     * @param minimum version minimale
     * @return raison du refus
     */
    public static String versionTooOld(MinecraftVersion actual, MinecraftVersion minimum) {
        return ConsoleText.tr("Minecraft {0} n'est pas supporte : NovaBank exige Minecraft {1} ou plus recent. NovaBank ne demarre pas.",
                actual, minimum);
    }

    /**
     * @param rawVersion texte fourni par le serveur
     * @return avertissement
     */
    public static String unknownVersionFormat(String rawVersion) {
        return ConsoleText.tr("Version du serveur non reconnue ({0}) : compatibilite non verifiee.", rawVersion);
    }

    /**
     * @param errors nombre d'erreurs
     * @return raison du refus
     */
    public static String configRejected(int errors) {
        return ConsoleText.tr("Configuration invalide ({0} erreur(s), detail ci-dessus) : NovaBank est desactive. Corrigez config.yml puis redemarrez le serveur.",
                errors);
    }

    /**
     * @param file fichier cree
     * @return ligne de journal
     */
    public static String defaultConfigCreated(Path file) {
        return ConsoleText.tr("Fichier de configuration par defaut cree : {0}", file);
    }

    /**
     * @param cause cause technique
     * @return message d'erreur de lecture
     */
    public static String configUnreadable(String cause) {
        return ConsoleText.tr("fichier illisible ({0})", cause);
    }

    /**
     * @param position {@code ligne:colonne} de l'erreur, si connue
     * @return message d'erreur de syntaxe
     */
    public static String configInvalidYaml(Optional<String> position) {
        return position.map(at -> ConsoleText.tr("syntaxe YAML invalide (ligne:colonne {0})", at))
                .orElse(ConsoleText.tr("syntaxe YAML invalide"));
    }

    /**
     * @param result bilan de la mise a jour d'un fichier de l'administrateur
     * @return compte rendu : cles ajoutees, cles retirees, cles obsoletes, sauvegarde, commentaires
     */
    public static String fileUpdated(YamlFileUpdater.Result result) {
        StringBuilder text = new StringBuilder(
                ConsoleText.tr("Fichier {0} mis a jour (version {1} -> {2}) : {3} cle(s) ajoutee(s)",
                        result.file(), result.fromVersion(), result.toVersion(), result.added().size()));
        if (!result.added().isEmpty()) {
            text.append(" ").append(result.added());
        }
        if (!result.removed().isEmpty()) {
            text.append(ConsoleText.tr(", {0} cle(s) retiree(s) car supprimee(s) de NovaBank {1}",
                    result.removed().size(),
                    result.removed()));
        }
        text.append(ConsoleText.tr(", vos valeurs sont conservees ; ancien fichier sauvegarde : {0}", result.backup()));
        if (!result.obsolete().isEmpty()) {
            text.append(ConsoleText.tr(". Cle(s) obsolete(s) conservee(s), a retirer si inutiles : {0}",
                    result.obsolete()));
        }
        if (!result.comments()) {
            text.append(
                    ConsoleText.tr(". Cette version du serveur (avant 1.18.1) ne conserve pas les commentaires : consultez la sauvegarde pour l'original commente"));
        }
        return text.toString();
    }

    /**
     * @param type    YAML ou JSON
     * @param library classe de la bibliotheque absente
     * @return refus : bibliotheque du format absente du serveur
     */
    public static String fileFormatLibraryMissing(StorageType type, String library) {
        return ConsoleText.tr("Stockage {0} impossible : bibliotheque du serveur absente ({1}). Choisissez un autre storage.type.",
                type, library);
    }

    /**
     * @param type   YAML ou JSON
     * @param reason cause (verrou d'un autre serveur, fichier illisible ou corrompu)
     * @return refus : stockage fichier non ouvrable
     */
    public static String fileStorageUnavailable(StorageType type, String reason) {
        return ConsoleText.tr("Stockage {0} indisponible : {1}", type, reason);
    }

    /**
     * @param location hotes et base, sans identifiants
     * @param reason   cause, deja expurgee de l'URI et du mot de passe
     * @return raison du refus
     */
    public static String mongoUnavailable(String location, String reason) {
        return ConsoleText.tr("Stockage MONGODB indisponible ({0}) : {1}. Verifiez storage.mongodb (uri, base, delais). NovaBank ne demarre pas.",
                location, reason);
    }

    /**
     * @param type   MYSQL ou MARIADB
     * @param reason cause nommant hote, port et base, deja expurgee du mot de passe
     * @return raison du refus
     */
    public static String sqlServerUnavailable(StorageType type, String reason) {
        return ConsoleText.tr("Stockage {0} indisponible : {1}. Verifiez storage.sql (hote, port, base, utilisateur, mot de passe, options). NovaBank ne demarre pas.",
                type, reason);
    }

    /**
     * @param folder dossier
     * @param cause  cause
     * @return raison du refus
     */
    public static String databaseFolderUnavailable(Path folder, Exception cause) {
        return ConsoleText.tr("Creation du dossier de la base impossible ({0}) : {1}", folder, cause.getMessage());
    }

    /**
     * @param type          backend
     * @param location      emplacement de la base
     * @param schemaVersion version du schema
     * @return ligne de journal
     */
    public static String storageReady(StorageType type, String location, int schemaVersion) {
        return ConsoleText.tr("Stockage {0} pret : {1} (schema v{2})", type, location, schemaVersion);
    }

    /**
     * @param version version du plugin
     * @param millis  duree du demarrage
     * @return ligne de journal
     */
    public static String started(String version, long millis) {
        return ConsoleText.tr("NovaBank {0} active en {1} ms.", version, millis);
    }

    /**
     * @return raison d'un echec inattendu
     */
    public static String unexpectedStartupFailure() {
        return ConsoleText.tr("Erreur inattendue au demarrage : NovaBank est desactive.");
    }

    /**
     * @return ligne de journal
     */
    public static String stopped() {
        return ConsoleText.tr("NovaBank arrete, aucun thread NovaBank restant.");
    }

    /**
     * @param threads noms des threads encore vivants
     * @return avertissement
     */
    public static String threadsRemaining(List<String> threads) {
        return ConsoleText.tr("NovaBank arrete, mais des threads NovaBank sont encore actifs : {0}",
                String.join(", ", threads));
    }

    // ---- Phase d'arret (journal) ----

    /**
     * @param operations operations en cours
     * @param timeoutMs  attente maximale
     * @return ligne de journal
     */
    public static String drainWaiting(int operations, long timeoutMs) {
        return ConsoleText.tr("Arret : attente de {0} operation(s) en cours (au plus {1} ms)", operations, timeoutMs);
    }

    /**
     * @param elapsedMs duree de l'attente
     * @return ligne de journal
     */
    public static String drainDone(long elapsedMs) {
        return ConsoleText.tr("Arret : operations en cours terminees en {0} ms", elapsedMs);
    }

    /**
     * @param operations operations non terminees
     * @param elapsedMs  duree de l'attente
     * @param abandoned  taches jamais executees
     * @return erreur
     */
    public static String drainIncomplete(int operations, long elapsedMs, int abandoned) {
        return ConsoleText.tr("Arret : {0} operation(s) non terminee(s) apres {1} ms ({2} tache(s) abandonnee(s)). Les retraits restes reserves seront soldes au prochain demarrage ; un depot interrompu est journalise ci-dessus.",
                operations, elapsedMs, abandoned);
    }
}
