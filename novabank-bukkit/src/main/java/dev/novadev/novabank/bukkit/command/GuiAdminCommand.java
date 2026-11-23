package dev.novadev.novabank.bukkit.command;

import dev.novadev.novabank.bukkit.config.ConfigChanges;
import dev.novadev.novabank.bukkit.gui.EditorMenus;
import dev.novadev.novabank.bukkit.gui.GuiEditor;
import dev.novadev.novabank.bukkit.message.Language;
import dev.novadev.novabank.config.model.GuiSettings;
import dev.novadev.novabank.core.access.Permissions;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import org.bukkit.entity.Player;

/**
 * {@code /novabank gui} : editeur visuel de la disposition du GUI de banque. Sans argument il ouvre le coffre d'edition
 * (le brouillon de l'administrateur, conserve apres la fermeture) ; {@code save} ecrit la disposition par le chemin commun
 * des modifications de configuration (sauvegarde du fichier, validation, rechargement, diffusion aux autres serveurs),
 * {@code cancel} abandonne le brouillon, {@code reset} remet la disposition par defaut dans le brouillon, {@code rows <n>}
 * change le nombre de lignes.
 */
public final class GuiAdminCommand implements AdminSubcommand {

    private static final List<String> ACTIONS = List.of("save", "cancel", "reset", "rows");

    private final Supplier<GuiSettings> current;
    private final Supplier<Language> language;
    private final EditorMenus menus;
    private final Function<UUID, Player> players;
    private final ConfigChanges changes;
    private final Map<UUID, GuiEditor> drafts = new ConcurrentHashMap<>();

    /**
     * @param current  disposition en vigueur
     * @param language langue en vigueur
     * @param menus    coffres d'edition
     * @param players  joueur connecte par UUID
     * @param changes  chemin commun des modifications de configuration
     */
    public GuiAdminCommand(Supplier<GuiSettings> current, Supplier<Language> language, EditorMenus menus,
                           Function<UUID, Player> players, ConfigChanges changes) {
        this.current = Objects.requireNonNull(current, "current");
        this.language = Objects.requireNonNull(language, "language");
        this.menus = Objects.requireNonNull(menus, "menus");
        this.players = Objects.requireNonNull(players, "players");
        this.changes = Objects.requireNonNull(changes, "changes");
    }

    @Override
    public String permission() {
        return Permissions.ADMIN_GUI;
    }

    @Override
    public List<String> complete(String[] args) {
        if (args.length == 1) {
            return ACTIONS;
        }
        return args.length == 2 && args[0].equalsIgnoreCase("rows") ? List.of("2", "3", "4", "5", "6") : List.of();
    }

    @Override
    public void execute(CommandActor actor, String label, String[] args) {
        Language text = language.get();
        UUID id = actor.playerId().orElse(null);
        if (id == null) {
            actor.send(text.chat("admin.gui.player-only"));
            return;
        }
        String action = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "" -> open(actor, label, id);
            case "save" -> save(actor, id);
            case "cancel" -> actor.send(text.chat(drafts.remove(id) != null ? "admin.gui.cancelled" : "admin.gui.no-draft",
                    "label", label));
            case "reset" -> {
                GuiEditor draft = draft(id);
                draft.reset();
                actor.send(text.chat("admin.gui.reset"));
                reopenIfOpen(actor, label, id, draft);
            }
            case "rows" -> rows(actor, label, id, args);
            default -> actor.send(text.chat("admin.gui.usage", "label", label));
        }
    }

    /**
     * @param id administrateur
     * @return son brouillon s'il differe de la disposition en vigueur
     */
    public java.util.Optional<GuiEditor> pending(UUID id) {
        return java.util.Optional.ofNullable(drafts.get(id)).filter(GuiEditor::dirty);
    }

    /**
     * @param id administrateur
     * @return son brouillon (cree depuis la disposition en vigueur s'il n'existe pas)
     */
    public GuiEditor draftOf(UUID id) {
        return draft(id);
    }

    /**
     * @param id administrateur dont le brouillon est abandonne
     */
    public void drop(UUID id) {
        drafts.remove(id);
    }

    /**
     * Ouvre le coffre d'edition.
     *
     * @param id      administrateur
     * @param onClose appele a la fermeture du coffre
     */
    public void openEditor(UUID id, Runnable onClose) {
        Player player = players.apply(id);
        if (player != null) {
            menus.open(player, draft(id), onClose);
        }
    }

    private GuiEditor draft(UUID id) {
        return drafts.computeIfAbsent(id, key -> new GuiEditor(current.get(), language));
    }

    private void open(CommandActor actor, String label, UUID id) {
        Player player = players.apply(id);
        if (player == null) {
            return;
        }
        GuiEditor draft = draft(id);
        menus.open(player, draft, () -> actor.send(language.get().chat("admin.gui.closed-hint", "label", label)));
    }

    private void reopenIfOpen(CommandActor actor, String label, UUID id, GuiEditor draft) {
        Player player = players.apply(id);
        if (player != null && menus.isOpen(id)) {
            menus.open(player, draft, () -> actor.send(language.get().chat("admin.gui.closed-hint", "label", label)));
        }
    }

    private void rows(CommandActor actor, String label, UUID id, String[] args) {
        Language text = language.get();
        int rows;
        try {
            rows = args.length == 2 ? Integer.parseInt(args[1]) : -1;
        } catch (NumberFormatException invalid) {
            rows = -1;
        }
        GuiEditor draft = draft(id);
        if (!draft.setRows(rows)) {
            actor.send(text.chat("admin.gui.rows-invalid"));
            return;
        }
        actor.send(text.chat("admin.gui.rows", "rows", rows));
        reopenIfOpen(actor, label, id, draft);
    }

    private void save(CommandActor actor, UUID id) {
        Language text = language.get();
        GuiEditor draft = drafts.get(id);
        if (draft == null || !draft.dirty()) {
            actor.send(text.chat("admin.gui.unchanged"));
            return;
        }
        changes.apply(Map.of("gui", draft.toBlock()), "gui", result -> {
            Language now = language.get();
            if (!result.write().applied()) {
                result.write().errors().forEach(error -> actor.send(now.chat("admin.gui.refused", "reason", error)));
                return;
            }
            drafts.remove(id);
            actor.send(now.chat("admin.gui.saved"));
            result.reload().filter(outcome -> !outcome.applied()).ifPresent(outcome -> actor.send(now.chat(
                    "admin.gui.not-reloaded", "errors", outcome.report().errors().size())));
            if (result.published()) {
                actor.send(now.chat("admin.config.deployed"));
            }
        });
    }
}
