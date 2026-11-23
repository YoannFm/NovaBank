package dev.novadev.novabank.bukkit.command;

import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.bukkit.config.ConfigChanges;
import dev.novadev.novabank.bukkit.gui.EditorMenus;
import dev.novadev.novabank.bukkit.gui.ItemPriceScreen;
import dev.novadev.novabank.bukkit.gui.ItemsEditor;
import dev.novadev.novabank.bukkit.message.Language;
import dev.novadev.novabank.core.access.Permissions;
import dev.novadev.novabank.core.application.TaskScheduler;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import org.bukkit.entity.Player;

/**
 * {@code /novabank items} : items acceptes et prix en jeu. Sans argument : coffre pagine (clic gauche +1, clic droit -1,
 * majuscule retire, barre du bas : pages et ajout de l'item en main). Commandes : {@code list}, {@code add <item|hand>
 * <prix> [categorie]}, {@code price <item> <prix>}, {@code remove <item>}, {@code category <item> <nom|->}, {@code name <item> <nom affiche|->}. Tout modifie le
 * BROUILLON de l'administrateur ; {@code save} l'ecrit par le chemin commun des modifications de configuration (sauvegarde
 * du fichier, validation, rechargement, diffusion aux autres serveurs), {@code cancel} l'abandonne.
 */
public final class ItemsAdminCommand implements AdminSubcommand {

    private static final List<String> ACTIONS = List.of("list", "add", "price", "remove", "category", "name", "save",
            "cancel");
    private static final int LIST_LIMIT = 40;
    private static final Map<ItemPriceScreen.Field, String> PROMPTS = Map.of(ItemPriceScreen.Field.PRICE,
            "admin.items.price-prompt", ItemPriceScreen.Field.CATEGORY, "admin.items.category-prompt",
            ItemPriceScreen.Field.NAME, "admin.items.name-prompt");
    private static final Map<ItemPriceScreen.Field, String> INVALID = Map.of(ItemPriceScreen.Field.PRICE,
            "admin.items.price-invalid", ItemPriceScreen.Field.CATEGORY, "admin.items.invalid-category",
            ItemPriceScreen.Field.NAME, "admin.items.name-invalid");

    private final Supplier<List<?>> rawItems;
    private final Supplier<Language> language;
    private final EditorMenus menus;
    private final Function<UUID, Player> players;
    private final Function<UUID, Optional<ItemKey>> heldItem;
    private final ConfigChanges changes;
    private final TaskScheduler scheduler;
    private final Map<UUID, ItemsEditor> drafts = new ConcurrentHashMap<>();
    /** Fermeture de la liste ouverte par chaque administrateur (reprise par l'ecran de prix). */
    private final Map<UUID, Runnable> listClosers = new ConcurrentHashMap<>();

    /**
     * @param rawItems liste {@code items} lue dans le fichier
     * @param language langue en vigueur
     * @param menus    coffres d'edition
     * @param players  joueur connecte par UUID
     * @param heldItem item tenu en main par un joueur
     * @param changes  chemin commun des modifications de configuration
     * @param scheduler planificateur (reprise de la saisie au clavier sur le thread du joueur)
     */
    public ItemsAdminCommand(Supplier<List<?>> rawItems, Supplier<Language> language, EditorMenus menus,
                             Function<UUID, Player> players, Function<UUID, Optional<ItemKey>> heldItem,
                             ConfigChanges changes, TaskScheduler scheduler) {
        this.rawItems = Objects.requireNonNull(rawItems, "rawItems");
        this.language = Objects.requireNonNull(language, "language");
        this.menus = Objects.requireNonNull(menus, "menus");
        this.players = Objects.requireNonNull(players, "players");
        this.heldItem = Objects.requireNonNull(heldItem, "heldItem");
        this.changes = Objects.requireNonNull(changes, "changes");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public String permission() {
        return Permissions.ADMIN_ITEMS;
    }

    @Override
    public List<String> complete(String[] args) {
        if (args.length == 1) {
            return ACTIONS;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("add")) {
            return List.of("hand");
        }
        return List.of();
    }

    @Override
    public void execute(CommandActor actor, String label, String[] args) {
        Language text = language.get();
        UUID id = actor.playerId().orElse(null);
        if (id == null) {
            actor.send(text.chat("admin.items.player-only"));
            return;
        }
        String action = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        String[] rest = args.length == 0 ? args : Arrays.copyOfRange(args, 1, args.length);
        switch (action) {
            case "" -> open(actor, label, id);
            case "list" -> list(actor, id);
            case "add" -> add(actor, label, id, rest);
            case "price" -> price(actor, label, id, rest);
            case "remove" -> remove(actor, label, id, rest);
            case "category" -> category(actor, label, id, rest);
            case "name" -> name(actor, label, id, rest);
            case "save" -> save(actor, id);
            case "cancel" -> actor.send(text.chat(drafts.remove(id) != null ? "admin.items.cancelled"
                    : "admin.items.no-draft", "label", label));
            default -> actor.send(text.chat("admin.items.usage", "label", label));
        }
    }

    /**
     * @param id administrateur
     * @return son brouillon s'il differe du fichier
     */
    public Optional<ItemsEditor> pending(UUID id) {
        return Optional.ofNullable(drafts.get(id)).filter(ItemsEditor::dirty);
    }

    /**
     * @param id administrateur
     * @return son brouillon (cree depuis le fichier s'il n'existe pas)
     */
    public ItemsEditor draftOf(UUID id) {
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
            listClosers.put(id, onClose);
            menus.open(player, draft(id), onClose);
        }
    }

    private ItemsEditor draft(UUID id) {
        return drafts.computeIfAbsent(id, key -> {
            ItemsEditor editor = new ItemsEditor(rawItems.get(), language);
            editor.onSelect(item -> openPrice(id, item));
            return editor;
        });
    }

    /** Ecran de prix d'un item ; le retour (barriere) rouvre la liste, Echap rend la main comme la liste. */
    private void openPrice(UUID id, ItemKey item) {
        Player player = players.apply(id);
        if (player == null) {
            return;
        }
        ItemsEditor editor = draft(id);
        Runnable closer = listClosers.getOrDefault(id, () -> { });
        Runnable back = () -> menus.open(player, editor, closer);
        Runnable[] self = new Runnable[1];
        self[0] = () -> menus.open(player, new ItemPriceScreen(language, editor, item, back, field -> {
            player.sendMessage(language.get().chat(PROMPTS.get(field)));
            menus.prompt(player, line -> scheduler.runForPlayer(id, () -> {
                applyTyped(player, editor, item, field, line);
                self[0].run();
            }, () -> { }));
        }), closer);
        self[0].run();
    }

    /** Applique une valeur tapee au chat ; {@code annuler} ne change rien, {@code -} retire la categorie ou le nom. */
    private void applyTyped(Player player, ItemsEditor editor, ItemKey item, ItemPriceScreen.Field field, String line) {
        String text = line.trim();
        if (text.equalsIgnoreCase("annuler") || text.equalsIgnoreCase("cancel")) {
            return;
        }
        String value = text.equals("-") ? null : text;
        boolean applied = switch (field) {
            case PRICE -> {
                try {
                    yield editor.setPrice(item, new BigDecimal(text.replace(',', '.')));
                } catch (NumberFormatException invalid) {
                    yield false;
                }
            }
            case CATEGORY -> editor.setCategory(item, value == null ? null : value.toLowerCase(Locale.ROOT));
            case NAME -> editor.setDisplayName(item, value);
        };
        if (!applied) {
            player.sendMessage(language.get().chat(INVALID.get(field), "value", text));
        }
    }

    private void open(CommandActor actor, String label, UUID id) {
        Player player = players.apply(id);
        if (player != null) {
            Runnable onClose = () -> actor.send(language.get().chat("admin.items.closed-hint", "label", label));
            listClosers.put(id, onClose);
            menus.open(player, draft(id), onClose);
        }
    }

    private void list(CommandActor actor, UUID id) {
        Language text = language.get();
        Map<String, String> summary = draft(id).summary();
        actor.send(text.chat("admin.items.list-header", "count", summary.size()));
        summary.entrySet().stream().limit(LIST_LIMIT).forEach(entry -> actor.send(text.chat("admin.items.list-line",
                "item", entry.getKey(), "value", entry.getValue())));
        if (summary.size() > LIST_LIMIT) {
            actor.send(text.chat("admin.items.list-more", "more", summary.size() - LIST_LIMIT));
        }
    }

    private void add(CommandActor actor, String label, UUID id, String[] args) {
        if (args.length < 2 || args.length > 3) {
            actor.send(language.get().chat("admin.items.usage", "label", label));
            return;
        }
        Optional<ItemKey> key = args[0].equalsIgnoreCase("hand") ? heldItem.apply(id) : item(actor, args[0]);
        if (key.isEmpty()) {
            if (args[0].equalsIgnoreCase("hand")) {
                actor.send(language.get().chat("admin.items.hand-empty"));
            }
            return;
        }
        BigDecimal price = price(actor, args[1]);
        if (price == null) {
            return;
        }
        String category = args.length == 3 ? args[2].toLowerCase(Locale.ROOT) : null;
        if (!draft(id).add(key.get(), price, category)) {
            actor.send(language.get().chat("admin.items.invalid-category", "value", args[2]));
            return;
        }
        edited(actor, label, key.get() + " = " + price.stripTrailingZeros().toPlainString());
    }

    private void price(CommandActor actor, String label, UUID id, String[] args) {
        if (args.length != 2) {
            actor.send(language.get().chat("admin.items.usage", "label", label));
            return;
        }
        Optional<ItemKey> key = item(actor, args[0]);
        BigDecimal price = key.isEmpty() ? null : price(actor, args[1]);
        if (key.isEmpty() || price == null) {
            return;
        }
        if (!draft(id).setPrice(key.get(), price)) {
            actor.send(language.get().chat("admin.items.unknown-item", "item", key.get()));
            return;
        }
        edited(actor, label, key.get() + " = " + price.stripTrailingZeros().toPlainString());
    }

    private void remove(CommandActor actor, String label, UUID id, String[] args) {
        Optional<ItemKey> key = args.length == 1 ? item(actor, args[0]) : Optional.empty();
        if (args.length != 1) {
            actor.send(language.get().chat("admin.items.usage", "label", label));
        } else if (key.isPresent()) {
            if (draft(id).remove(key.get())) {
                edited(actor, label, "- " + key.get());
            } else {
                actor.send(language.get().chat("admin.items.unknown-item", "item", key.get()));
            }
        }
    }

    private void category(CommandActor actor, String label, UUID id, String[] args) {
        if (args.length != 2) {
            actor.send(language.get().chat("admin.items.usage", "label", label));
            return;
        }
        Optional<ItemKey> key = item(actor, args[0]);
        if (key.isEmpty()) {
            return;
        }
        String category = args[1].equals("-") ? null : args[1].toLowerCase(Locale.ROOT);
        if (!draft(id).setCategory(key.get(), category)) {
            actor.send(language.get().chat(draft(id).entries().stream().anyMatch(entry -> entry.key().equals(key.get()))
                    ? "admin.items.invalid-category" : "admin.items.unknown-item", "value", args[1], "item", key.get()));
            return;
        }
        edited(actor, label, key.get() + " [" + (category == null ? "-" : category) + "]");
    }

    /** {@code name <item> <nom affiche...|->} : le nom peut contenir des espaces et des codes couleur. */
    private void name(CommandActor actor, String label, UUID id, String[] args) {
        if (args.length < 2) {
            actor.send(language.get().chat("admin.items.usage", "label", label));
            return;
        }
        Optional<ItemKey> key = item(actor, args[0]);
        if (key.isEmpty()) {
            return;
        }
        String typed = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        String name = typed.equals("-") ? null : typed;
        if (!draft(id).setDisplayName(key.get(), name)) {
            actor.send(language.get().chat("admin.items.unknown-item", "item", key.get()));
            return;
        }
        edited(actor, label, key.get() + " \"" + (name == null ? "-" : name) + "\"");
    }

    private Optional<ItemKey> item(CommandActor actor, String text) {
        try {
            return Optional.of(ItemKey.parse(text.contains(":") ? text : "minecraft:" + text));
        } catch (IllegalArgumentException invalid) {
            actor.send(language.get().chat("admin.items.invalid-item", "value", text));
            return Optional.empty();
        }
    }

    private BigDecimal price(CommandActor actor, String text) {
        try {
            BigDecimal price = new BigDecimal(text.replace(',', '.'));
            if (price.signum() >= 0 && price.scale() <= 8 && price.precision() <= 18) {
                return price;
            }
        } catch (NumberFormatException invalid) {
            // Message ci-dessous.
        }
        actor.send(language.get().chat("admin.items.invalid-price", "value", text));
        return null;
    }

    private void edited(CommandActor actor, String label, String summary) {
        actor.send(language.get().chat("admin.items.edited", "summary", summary, "label", label));
    }

    private void save(CommandActor actor, UUID id) {
        Language text = language.get();
        ItemsEditor draft = drafts.get(id);
        if (draft == null || !draft.dirty()) {
            actor.send(text.chat("admin.items.unchanged"));
            return;
        }
        changes.apply(Map.of("items", draft.toBlock()), "items", result -> {
            Language now = language.get();
            if (!result.write().applied()) {
                result.write().errors().forEach(error -> actor.send(now.chat("admin.items.refused", "reason", error)));
                return;
            }
            drafts.remove(id);
            actor.send(now.chat("admin.items.saved"));
            result.reload().filter(outcome -> !outcome.applied()).ifPresent(outcome -> actor.send(now.chat(
                    "admin.gui.not-reloaded", "errors", outcome.report().errors().size())));
            if (result.published()) {
                actor.send(now.chat("admin.config.deployed"));
            }
        });
    }
}
