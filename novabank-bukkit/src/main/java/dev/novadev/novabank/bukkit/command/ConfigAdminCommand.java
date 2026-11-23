package dev.novadev.novabank.bukkit.command;

import dev.novadev.novabank.bukkit.config.ConfigChanges;
import dev.novadev.novabank.bukkit.config.ConfigPayload;
import dev.novadev.novabank.bukkit.config.ConfigWriter;
import dev.novadev.novabank.bukkit.config.YamlScalarEditor;
import dev.novadev.novabank.bukkit.config.YamlSections;
import dev.novadev.novabank.bukkit.message.Language;
import dev.novadev.novabank.config.ConfigEditor;
import dev.novadev.novabank.config.ConfigLoadResult;
import dev.novadev.novabank.config.EditOutcome;
import dev.novadev.novabank.config.tree.SecretPaths;
import dev.novadev.novabank.core.application.TaskScheduler;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

/**
 * {@code /novabank config get <chemin>} et {@code /novabank config set <chemin> <valeur>} : configuration en jeu
 * (CDC 12). La modification est d'abord revalidee en entier par {@link ConfigEditor} (rien n'est ecrit si elle est
 * refusee), puis ecrite dans {@code config.yml} en conservant les commentaires ({@link YamlScalarEditor}), apres une
 * sauvegarde horodatee dans {@code backup/}, puis appliquee par le rechargement habituel (propage aux autres serveurs si la
 * synchronisation est active). Les secrets (mots de passe, URI) restent modifiables dans le fichier UNIQUEMENT (D5) et
 * s'affichent masques. Limite : seules les cles deja presentes avec une valeur sur leur ligne sont modifiables.
 * Une section partagee (voir {@link ConfigPayload#SHARED}) est en plus diffusee aux autres serveurs quand le deploiement
 * Redis est actif.
 */
public final class ConfigAdminCommand {

    private final Supplier<ConfigLoadResult> current;
    private final ConfigEditor editor;
    private final Path file;
    private final ConfigWriter writer;
    private final ConfigChanges changes;
    private final TaskScheduler scheduler;
    private final AdminActions reload;
    private final Supplier<Language> language;

    /**
     * @param current   lecture de la configuration actuelle du fichier
     * @param editor    validation d'une modification
     * @param file      fichier {@code config.yml}
     * @param writer    ecriture sauvegardee (sections propres au serveur : storage, redis, server)
     * @param changes   chemin des sections partagees : ecriture validee, rechargement, diffusion aux autres serveurs
     * @param scheduler planificateur (ecriture sur l'executeur asynchrone)
     * @param reload    rechargement apres ecriture
     * @param language  langue en vigueur
     */
    public ConfigAdminCommand(Supplier<ConfigLoadResult> current, ConfigEditor editor, Path file,
                              ConfigWriter writer, ConfigChanges changes, TaskScheduler scheduler, AdminActions reload, Supplier<Language> language) {
        this.current = Objects.requireNonNull(current, "current");
        this.editor = Objects.requireNonNull(editor, "editor");
        this.file = Objects.requireNonNull(file, "file");
        this.writer = Objects.requireNonNull(writer, "writer");
        this.changes = Objects.requireNonNull(changes, "changes");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.reload = Objects.requireNonNull(reload, "reload");
        this.language = Objects.requireNonNull(language, "language");
    }

    /**
     * {@code /novabank config push} : renvoie aux autres serveurs les sections partagees de ce serveur.
     *
     * @param actor administrateur
     */
    public void push(CommandActor actor) {
        Language text = language.get();
        actor.send(text.chat(changes.push() ? "admin.config.pushed" : "admin.config.push-failed"));
    }

    /**
     * @param actor administrateur
     * @param path  chemin lu
     */
    public void get(CommandActor actor, String path) {
        Language text = language.get();
        if (SecretPaths.touchesSecret(path)) {
            actor.send(text.chat("admin.config.value", "path", path, "value", "****"));
            return;
        }
        Object value = lookup(current.get().tree(), path);
        actor.send(value == null ? text.chat("admin.config.unknown", "path", path)
                : text.chat("admin.config.value", "path", path, "value", String.valueOf(value)));
    }

    /**
     * @param actor administrateur
     * @param path  chemin modifie
     * @param value nouvelle valeur (texte)
     */
    public void set(CommandActor actor, String path, String value) {
        Language text = language.get();
        EditOutcome outcome = editor.edit(current.get(), path, value);
        if (!outcome.applied()) {
            outcome.report().errors().forEach(error -> actor.send(text.chat("admin.config.refused", "path",
                    error.path(), "reason", error.message())));
            return;
        }
        java.util.logging.Logger.getLogger("NovaBank").info("Configuration en jeu : " + actor.name() + " modifie " + path);
        try {
            scheduler.runAsync(() -> {
                String top = path.contains(".") ? path.substring(0, path.indexOf('.')) : path;
                String failure = null;
                String edited = null;
                try {
                    edited = YamlScalarEditor.replace(Files.readString(file, StandardCharsets.UTF_8), path, value)
                            .orElse(null);
                } catch (IOException unreadable) {
                    failure = unreadable.getClass().getSimpleName();
                }
                if (edited == null && failure == null) {
                    failure = "cle absente du fichier ou non scalaire : modifiez config.yml a la main";
                }
                if (failure != null) {
                    String reason = failure;
                    scheduler.runGlobal(() -> actor.send(language.get().chat("admin.config.not-written", "path", path,
                            "reason", reason)));
                    return;
                }
                String yaml = edited;
                if (ConfigPayload.SHARED.contains(top)) {
                    // Section partagee : ecriture validee et sauvegardee, rechargement, puis diffusion aux autres serveurs.
                    String block = YamlSections.extract(yaml, top).orElseThrow();
                    scheduler.runGlobal(() -> changes.apply(Map.of(top, block), "edit",
                            result -> report(actor, path, value, result)));
                    return;
                }
                String localFailure = writeLocal(yaml);
                scheduler.runGlobal(() -> {
                    Language now = language.get();
                    if (localFailure != null) {
                        actor.send(now.chat("admin.config.not-written", "path", path, "reason", localFailure));
                        return;
                    }
                    actor.send(now.chat("admin.config.saved", "path", path, "value", value));
                    reload.reload(actor);
                });
            });
        } catch (RejectedExecutionException saturated) {
            actor.send(text.chat("general.storage-error"));
        }
    }

    private void report(CommandActor actor, String path, String value, ConfigChanges.Result result) {
        Language now = language.get();
        if (!result.write().applied()) {
            result.write().errors().forEach(error -> actor.send(now.chat("admin.config.refused", "path", path,
                    "reason", error)));
            return;
        }
        actor.send(now.chat("admin.config.saved", "path", path, "value", value));
        result.reload().ifPresent(outcome -> {
            if (!outcome.applied()) {
                actor.send(now.chat("admin.reload.refused", "errors", outcome.report().errors().size()));
            }
        });
        if (result.published()) {
            actor.send(now.chat("admin.config.deployed"));
        }
    }

    /** @return {@code null} si ecrit, sinon la raison de l'echec */
    private String writeLocal(String yaml) {
        try {
            writer.write(yaml, "edit");
            return null;
        } catch (IOException failure) {
            return failure.getClass().getSimpleName();
        }
    }

    private static Object lookup(Map<String, Object> tree, String path) {
        Object node = tree;
        for (String key : path.split("\\.")) {
            if (!(node instanceof Map<?, ?> map) || !map.containsKey(key)) {
                return null;
            }
            node = map.get(key);
        }
        return node;
    }
}
