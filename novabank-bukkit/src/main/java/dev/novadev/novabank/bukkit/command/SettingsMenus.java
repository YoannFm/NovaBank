package dev.novadev.novabank.bukkit.command;

import dev.novadev.novabank.bukkit.config.Setting;
import dev.novadev.novabank.bukkit.config.SettingsCatalog;
import dev.novadev.novabank.bukkit.config.SettingsCatalog.Category;
import dev.novadev.novabank.bukkit.config.SettingsDraft;
import dev.novadev.novabank.bukkit.gui.EditorMenus;
import dev.novadev.novabank.bukkit.gui.EditorScreen;
import dev.novadev.novabank.bukkit.gui.SettingsScreens;
import dev.novadev.novabank.bukkit.message.Language;
import dev.novadev.novabank.core.access.Permissions;
import dev.novadev.novabank.core.application.TaskScheduler;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import org.bukkit.entity.Player;

/**
 * Menu des parametres du menu d'administration (comparateur) : un brouillon {@link SettingsDraft} par administrateur, lu
 * dans {@code config.yml} a la premiere ouverture et conserve jusqu'a « Enregistrer » ou « Abandonner » du menu
 * d'administration. Les valeurs textuelles se saisissent dans le chat (la ligne tapee est interceptee : elle n'est ni
 * diffusee ni ecrite dans la console, contrairement a une commande) ; un secret n'est jamais affiche.
 */
public final class SettingsMenus {

    private final Supplier<Map<String, Object>> rawConfig;
    private final Supplier<Collection<String>> knownTypes;
    private final Supplier<Language> language;
    private final EditorMenus menus;
    private final Function<UUID, Player> players;
    private final TaskScheduler scheduler;
    private final Map<UUID, SettingsDraft> drafts = new ConcurrentHashMap<>();

    /**
     * @param rawConfig  contenu actuel de {@code config.yml} (tables imbriquees)
     * @param knownTypes types de banques fournis par un plugin sur ce serveur
     * @param language   langue en vigueur
     * @param menus      coffres d'edition
     * @param players    joueur connecte par UUID
     * @param scheduler  planificateur (retour au thread du joueur apres une saisie)
     */
    public SettingsMenus(Supplier<Map<String, Object>> rawConfig, Supplier<Collection<String>> knownTypes,
                         Supplier<Language> language, EditorMenus menus, Function<UUID, Player> players,
                         TaskScheduler scheduler) {
        this.rawConfig = Objects.requireNonNull(rawConfig, "rawConfig");
        this.knownTypes = Objects.requireNonNull(knownTypes, "knownTypes");
        this.language = Objects.requireNonNull(language, "language");
        this.menus = Objects.requireNonNull(menus, "menus");
        this.players = Objects.requireNonNull(players, "players");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /**
     * @param id administrateur
     * @return son brouillon s'il differe du fichier
     */
    public Optional<SettingsDraft> pending(UUID id) {
        return Optional.ofNullable(drafts.get(id)).filter(SettingsDraft::dirty);
    }

    /**
     * @param id administrateur
     * @return son brouillon (cree depuis le fichier s'il n'existe pas)
     */
    public SettingsDraft draftOf(UUID id) {
        return drafts.computeIfAbsent(id, key -> new SettingsDraft(rawConfig.get()));
    }

    /**
     * @param id administrateur dont le brouillon est abandonne (ou enregistre)
     */
    public void drop(UUID id) {
        drafts.remove(id);
    }

    /**
     * Ouvre l'accueil des parametres.
     *
     * @param actor administrateur
     * @param hub   retour au menu d'administration (bouton retour et touche Echap)
     */
    public void open(CommandActor actor, Runnable hub) {
        actor.playerId().ifPresent(id -> new Session(actor, id, hub).home());
    }

    /** Navigation d'un administrateur entre les ecrans. */
    private final class Session implements SettingsScreens.Navigation {

        private final CommandActor actor;
        private final UUID id;
        private final Runnable hub;
        private Category category;

        Session(CommandActor actor, UUID id, Runnable hub) {
            this.actor = actor;
            this.id = id;
            this.hub = hub;
        }

        private SettingsDraft draft() {
            return draftOf(id);
        }

        private List<Category> categories() {
            return SettingsCatalog.categories(draft().types());
        }

        private void show(EditorScreen screen) {
            Player player = players.apply(id);
            if (player != null) {
                // Echap : retour au menu d'administration (le changement d'ecran, lui, ne declenche pas ce retour).
                menus.open(player, screen, hub);
            }
        }

        @Override
        public void hub() {
            hub.run();
        }

        @Override
        public void home() {
            show(new SettingsScreens.Home(language, draft(), categories(), this));
        }

        @Override
        public void category(Category chosen) {
            category = chosen;
            show(new SettingsScreens.Page(language, draft(), chosen, this));
        }

        @Override
        public void special(Setting setting) {
            Runnable back = () -> category(refreshed());
            switch (setting.kind()) {
                case TYPES -> show(new SettingsScreens.Types(language, draft(), this::selectableTypes, false, back));
                case PRIORITY -> show(new SettingsScreens.Types(language, draft(), () -> draft().types(), true, back));
                case ROLES -> show(new SettingsScreens.Roles(language, draft(), back));
                default -> { }
            }
        }

        /** Categorie courante relue (la liste des classements suit les types actifs). */
        private Category refreshed() {
            return categories().stream().filter(candidate -> candidate.id().equals(category.id())).findFirst()
                    .orElse(category);
        }

        private List<String> selectableTypes() {
            Set<String> types = new LinkedHashSet<>();
            types.add("player");
            types.addAll(draft().types());
            types.addAll(knownTypes.get());
            return new ArrayList<>(types);
        }

        @Override
        public void ask(Setting setting) {
            Player player = players.apply(id);
            if (player == null) {
                return;
            }
            Language text = language.get();
            String name = name(text, setting);
            boolean secret = setting.kind() == Setting.Kind.SECRET;
            player.sendMessage(text.chat(secret ? "settings.prompt-secret" : "settings.prompt", "setting", name));
            menus.prompt(player, line -> scheduler.runForPlayer(id, () -> {
                String typed = line.trim();
                if (!typed.equalsIgnoreCase("annuler") && !typed.equalsIgnoreCase("cancel")) {
                    Optional<Object> value = SettingsScreens.parse(setting, typed);
                    if (value.isPresent()) {
                        draft().set(setting.path(), value.get());
                        player.sendMessage(language.get().chat("settings.edited", "setting", name));
                    } else {
                        player.sendMessage(secret ? language.get().chat("settings.invalid-secret", "setting", name)
                                : language.get().chat("settings.invalid", "setting", name, "value", typed));
                    }
                }
                category(refreshed());
            }, () -> { }));
        }

        @Override
        public boolean secretsAllowed() {
            return actor.hasPermission(Permissions.ADMIN_SECRETS);
        }

        @Override
        public void secretDenied() {
            actor.send(language.get().chat("settings.secret-denied"));
        }

        private String name(Language text, Setting setting) {
            String type = setting.path().startsWith("leaderboards.") ? setting.path().split("\\.")[1] : "";
            return org.bukkit.ChatColor.stripColor(text.text("settings.entries." + setting.textKey() + ".name", "type",
                    type));
        }
    }
}
