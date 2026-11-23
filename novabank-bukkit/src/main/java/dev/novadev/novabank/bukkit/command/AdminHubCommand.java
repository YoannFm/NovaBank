package dev.novadev.novabank.bukkit.command;

import dev.novadev.novabank.bukkit.config.ConfigChanges;
import dev.novadev.novabank.bukkit.config.SettingsDraft;
import dev.novadev.novabank.bukkit.config.YamlSections;
import dev.novadev.novabank.bukkit.gui.AdminHub;
import dev.novadev.novabank.bukkit.gui.ConfirmScreen;
import dev.novadev.novabank.bukkit.gui.EditorMenus;
import dev.novadev.novabank.bukkit.gui.HubModel;
import dev.novadev.novabank.bukkit.message.Language;
import dev.novadev.novabank.core.access.Permissions;
import dev.novadev.novabank.core.application.TaskScheduler;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import org.bukkit.entity.Player;

/**
 * {@code /bank admin} (et {@code /novabank admin}) : menu principal d'administration. Ses boutons ouvrent l'editeur d'items
 * et l'editeur de GUI, changent le nombre de lignes, enregistrent ou abandonnent les brouillons (une seule ecriture
 * sauvegardee, validee, rechargee et deployee), renvoient la configuration aux autres serveurs et la rechargent. Echap dans un
 * editeur ramene a ce menu. Chaque bouton verifie la permission de l'editeur qu'il commande.
 */
public final class AdminHubCommand implements AdminSubcommand {

    private final GuiAdminCommand guis;
    private final ItemsAdminCommand items;
    private final SettingsMenus settings;
    private final EditorMenus menus;
    private final Function<UUID, Player> players;
    private final ConfigChanges changes;
    private final AdminActions actions;
    private final TaskScheduler scheduler;
    private final Supplier<Language> language;

    /**
     * @param guis      editeur de GUI (brouillons)
     * @param items     editeur d'items (brouillons)
     * @param settings  menu des parametres (brouillons)
     * @param menus     coffres d'edition
     * @param players   joueur connecte par UUID
     * @param changes   chemin commun des modifications de configuration
     * @param actions   rechargement
     * @param scheduler planificateur (retour au menu au tick suivant)
     * @param language  langue en vigueur
     */
    public AdminHubCommand(GuiAdminCommand guis, ItemsAdminCommand items, SettingsMenus settings, EditorMenus menus,
                           Function<UUID, Player> players, ConfigChanges changes, AdminActions actions,
                           TaskScheduler scheduler, Supplier<Language> language) {
        this.guis = Objects.requireNonNull(guis, "guis");
        this.items = Objects.requireNonNull(items, "items");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.menus = Objects.requireNonNull(menus, "menus");
        this.players = Objects.requireNonNull(players, "players");
        this.changes = Objects.requireNonNull(changes, "changes");
        this.actions = Objects.requireNonNull(actions, "actions");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.language = Objects.requireNonNull(language, "language");
    }

    @Override
    public String permission() {
        return Permissions.ADMIN;
    }

    @Override
    public List<String> complete(String[] args) {
        return List.of();
    }

    @Override
    public void execute(CommandActor actor, String label, String[] args) {
        UUID id = actor.playerId().orElse(null);
        if (id == null) {
            actor.send(language.get().chat("admin.hub.player-only"));
            return;
        }
        openHub(actor, label, id);
    }

    private void openHub(CommandActor actor, String label, UUID id) {
        Player player = players.apply(id);
        if (player == null) {
            return;
        }
        menus.open(player, new AdminHub(language, new Model(actor, label, id)), () -> {
            if (guis.pending(id).isPresent() || items.pending(id).isPresent() || settings.pending(id).isPresent()) {
                actor.send(language.get().chat("admin.hub.closed-hint", "label", label));
            }
        });
    }

    /** Retour au menu principal quand un editeur se ferme (au tick suivant : on est dans l'evenement de fermeture). */
    private Runnable backToHub(CommandActor actor, String label, UUID id) {
        return () -> scheduler.runForPlayer(id, () -> openHub(actor, label, id), () -> { });
    }

    private final class Model implements HubModel {

        private final CommandActor actor;
        private final String label;
        private final UUID id;

        Model(CommandActor actor, String label, UUID id) {
            this.actor = actor;
            this.label = label;
            this.id = id;
        }

        @Override
        public int itemsCount() {
            return items.draftOf(id).entries().size();
        }

        @Override
        public boolean itemsDirty() {
            return items.pending(id).isPresent();
        }

        @Override
        public int guiRows() {
            return guis.draftOf(id).rows();
        }

        @Override
        public boolean guiDirty() {
            return guis.pending(id).isPresent();
        }

        @Override
        public boolean settingsDirty() {
            return settings.pending(id).isPresent();
        }

        @Override
        public void openSettings() {
            if (allowed(Permissions.ADMIN_CONFIG)) {
                settings.open(actor, backToHub(actor, label, id));
            }
        }

        @Override
        public void openItems() {
            if (allowed(Permissions.ADMIN_ITEMS)) {
                items.openEditor(id, backToHub(actor, label, id));
            }
        }

        @Override
        public void openGui() {
            if (allowed(Permissions.ADMIN_GUI)) {
                guis.openEditor(id, backToHub(actor, label, id));
            }
        }

        @Override
        public void changeRows(int delta) {
            if (allowed(Permissions.ADMIN_GUI)) {
                guis.draftOf(id).setRows(guis.draftOf(id).rows() + delta);
            }
        }

        @Override
        public void resetGui() {
            if (allowed(Permissions.ADMIN_GUI)) {
                guis.draftOf(id).reset();
            }
        }

        @Override
        public boolean canPush() {
            return changes.canPush();
        }

        @Override
        public void confirm(Runnable action) {
            Player player = players.apply(id);
            if (player != null) {
                menus.open(player, new ConfirmScreen(language, () -> {
                    openHub(actor, label, id);
                    action.run();
                }, () -> openHub(actor, label, id)), () -> { });
            }
        }

        @Override
        public void save() {
            Map<String, String> blocks = new LinkedHashMap<>();
            if (actor.hasPermission(Permissions.ADMIN_GUI)) {
                guis.pending(id).ifPresent(draft -> blocks.put("gui", draft.toBlock()));
            }
            if (actor.hasPermission(Permissions.ADMIN_ITEMS)) {
                items.pending(id).ifPresent(draft -> blocks.put("items", draft.toBlock()));
            }
            Optional<SettingsDraft> draft = actor.hasPermission(Permissions.ADMIN_CONFIG) ? settings.pending(id)
                    : Optional.empty();
            if (blocks.isEmpty() && draft.isEmpty()) {
                actor.send(language.get().chat("admin.hub.nothing"));
                return;
            }
            Set<String> sections = new LinkedHashSet<>(blocks.keySet());
            draft.ifPresent(found -> sections.addAll(found.sections()));
            // Une seule ecriture : sections des editeurs, puis reglages du menu des parametres (commentaires conserves).
            UnaryOperator<String> change = text -> {
                String result = text;
                for (Map.Entry<String, String> block : blocks.entrySet()) {
                    result = YamlSections.replace(result, block.getKey(), block.getValue());
                }
                return draft.isPresent() ? draft.get().applyTo(result) : result;
            };
            changes.applyEdit(change, sections, "admin", result -> {
                Language now = language.get();
                if (!result.write().applied()) {
                    result.write().errors().forEach(error -> actor.send(now.chat("admin.hub.refused", "reason", error)));
                    return;
                }
                if (blocks.containsKey("gui")) {
                    guis.drop(id);
                }
                if (blocks.containsKey("items")) {
                    items.drop(id);
                }
                draft.ifPresent(found -> settings.drop(id));
                actor.send(now.chat("admin.hub.saved"));
                result.reload().ifPresent(outcome -> {
                    if (!outcome.applied()) {
                        actor.send(now.chat("admin.gui.not-reloaded", "errors", outcome.report().errors().size()));
                    } else if (outcome.restartRequired()) {
                        actor.send(now.chat("admin.reload.restart-required"));
                    }
                });
                if (result.published()) {
                    actor.send(now.chat("admin.config.deployed"));
                }
                menus.refresh(id);
            });
        }

        @Override
        public void cancel() {
            boolean any = guis.pending(id).isPresent() || items.pending(id).isPresent() || settings.pending(id).isPresent();
            guis.drop(id);
            items.drop(id);
            settings.drop(id);
            actor.send(language.get().chat(any ? "admin.hub.cancelled" : "admin.hub.nothing"));
        }

        @Override
        public void push() {
            if (allowed(Permissions.ADMIN_CONFIG)) {
                actor.send(language.get().chat(changes.push() ? "admin.config.pushed" : "admin.config.push-failed"));
            }
        }

        @Override
        public void reload() {
            actions.reload(actor);
        }

        private boolean allowed(String permission) {
            if (actor.hasPermission(permission)) {
                return true;
            }
            actor.send(language.get().chat("general.no-permission"));
            return false;
        }
    }
}
