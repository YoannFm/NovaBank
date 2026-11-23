package dev.novadev.novabank.bukkit.gui;

import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.bukkit.config.Setting;
import dev.novadev.novabank.bukkit.config.SettingsCatalog.Category;
import dev.novadev.novabank.bukkit.config.SettingsDraft;
import dev.novadev.novabank.bukkit.message.Language;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Ecrans du menu des parametres (comparateur du menu d'administration), sans dependance serveur : accueil des
 * categories, page d'une categorie, types de banques, ordre de choix et droits des roles. Chaque ecran lit et modifie le
 * brouillon {@link SettingsDraft} ; rien n'est ecrit avant « Enregistrer et deployer ». Barre du bas : retour en case 49.
 */
public final class SettingsScreens {

    /** Case du bouton retour de chaque ecran de 6 lignes. */
    static final int BACK = 49;
    static final int PREVIOUS = 45;
    static final int NEXT = 53;
    private static final ItemKey BACK_ICON = ItemKey.minecraft("arrow");
    private static final ItemKey FILLER = ItemKey.minecraft("gray_stained_glass_pane");
    private static final java.util.regex.Pattern OPTION_KEY = java.util.regex.Pattern.compile("[A-Za-z0-9_.-]+");
    private static final Map<String, String> TYPE_ICONS = Map.ofEntries(Map.entry("player", "player_head"),
            Map.entry("island", "grass_block"), Map.entry("town", "bell"), Map.entry("faction", "red_banner"),
            Map.entry("kingdom", "golden_helmet"), Map.entry("clan", "shield"), Map.entry("land", "map"),
            Map.entry("claim", "golden_shovel"), Map.entry("plot", "oak_fence"), Map.entry("residence", "oak_door"),
            Map.entry("region", "wooden_axe"));

    /** Actions demandees par les ecrans, fournies par la commande (ouverture des coffres, saisie au clavier). */
    public interface Navigation {

        /** Retour au menu d'administration. */
        void hub();

        /** Accueil des parametres. */
        void home();

        /** @param category page d'une categorie */
        void category(Category category);

        /** @param setting reglage a ouvrir dans son ecran dedie (types, ordre, roles) */
        void special(Setting setting);

        /** @param setting reglage dont la valeur est a saisir dans le chat */
        void ask(Setting setting);

        /** @return {@code true} si l'administrateur peut modifier les secrets */
        boolean secretsAllowed();

        /** Signale qu'un secret ne peut pas etre modifie sans permission. */
        void secretDenied();
    }

    private SettingsScreens() {
    }

    // ------------------------------------------------------------------ accueil

    /** Accueil : une icone par categorie (cases {@link #HOME_SLOTS}), retour au menu d'administration en case 31. */
    public static final class Home implements EditorScreen {

        static final int[] HOME_SLOTS = {10, 11, 12, 13, 14, 15, 16, 21, 23};
        static final int HOME_BACK = 31;

        private final Supplier<Language> language;
        private final SettingsDraft draft;
        private final List<Category> categories;
        private final Navigation navigation;

        /**
         * @param language   langue en vigueur
         * @param draft      brouillon
         * @param categories categories (au plus 9)
         * @param navigation actions
         */
        public Home(Supplier<Language> language, SettingsDraft draft, List<Category> categories, Navigation navigation) {
            this.language = Objects.requireNonNull(language, "language");
            this.draft = Objects.requireNonNull(draft, "draft");
            this.categories = List.copyOf(categories);
            this.navigation = Objects.requireNonNull(navigation, "navigation");
        }

        @Override
        public String title() {
            return language.get().text("settings.title");
        }

        @Override
        public int size() {
            return 36;
        }

        @Override
        public Optional<SlotView> slot(int slot) {
            Language text = language.get();
            for (int i = 0; i < categories.size() && i < HOME_SLOTS.length; i++) {
                if (HOME_SLOTS[i] == slot) {
                    Category category = categories.get(i);
                    long changed = category.settings().stream().filter(setting -> changedIn(draft, setting)).count();
                    return Optional.of(new SlotView(ItemKey.minecraft(category.icon()),
                            text.text("settings.categories." + category.id() + ".name"),
                            SlotView.lines(text.text("settings.categories." + category.id() + ".lore") + "\n"
                                    + text.text(changed > 0 ? "settings.changed-count" : "settings.clean", "count", changed))));
                }
            }
            if (slot == HOME_BACK) {
                return Optional.of(new SlotView(BACK_ICON, text.text("settings.back-hub"),
                        SlotView.lines(text.text(draft.dirty() ? "settings.back-hub-dirty" : "settings.clean", "count",
                                draft.changeCount()))));
            }
            return Optional.of(new SlotView(FILLER, " ", List.of()));
        }

        @Override
        public void click(int slot, MenuClick click, Optional<ItemKey> inHand) {
            if (click == MenuClick.OTHER) {
                return;
            }
            for (int i = 0; i < categories.size() && i < HOME_SLOTS.length; i++) {
                if (HOME_SLOTS[i] == slot) {
                    navigation.category(categories.get(i));
                    return;
                }
            }
            if (slot == HOME_BACK) {
                navigation.hub();
            }
        }
    }

    // ------------------------------------------------------------------ page d'une categorie

    /** Page d'une categorie : un reglage par case (0 a 44), retour a l'accueil en case 49. */
    public static final class Page implements EditorScreen {

        private final Supplier<Language> language;
        private final SettingsDraft draft;
        private final Category category;
        private final Navigation navigation;

        /**
         * @param language   langue en vigueur
         * @param draft      brouillon
         * @param category   categorie affichee
         * @param navigation actions
         */
        public Page(Supplier<Language> language, SettingsDraft draft, Category category, Navigation navigation) {
            this.language = Objects.requireNonNull(language, "language");
            this.draft = Objects.requireNonNull(draft, "draft");
            this.category = Objects.requireNonNull(category, "category");
            this.navigation = Objects.requireNonNull(navigation, "navigation");
        }

        @Override
        public String title() {
            return language.get().text("settings.page-title", "category",
                    language.get().text("settings.categories." + category.id() + ".name"));
        }

        @Override
        public int size() {
            return 54;
        }

        @Override
        public Optional<SlotView> slot(int slot) {
            Language text = language.get();
            if (slot == BACK) {
                return Optional.of(new SlotView(BACK_ICON, text.text("settings.back"), List.of()));
            }
            if (slot >= 45 || slot >= category.settings().size()) {
                return Optional.empty();
            }
            Setting setting = category.settings().get(slot);
            String type = setting.variable();
            String key = "settings.entries." + setting.textKey();
            List<String> lore = new ArrayList<>();
            lore.add(text.text("settings.value", "value", display(text, setting)));
            lore.addAll(SlotView.lines(text.text(key + ".desc", "type", type)));
            if (changedIn(draft, setting)) {
                lore.add(text.text("settings.modified"));
            }
            if (setting.restart()) {
                lore.add(text.text("settings.restart"));
            }
            lore.add("");
            lore.addAll(SlotView.lines(text.text("settings.hint." + hint(setting))));
            return Optional.of(new SlotView(ItemKey.minecraft(setting.icon()), text.text(key + ".name", "type", type), lore));
        }

        @Override
        public void click(int slot, MenuClick click, Optional<ItemKey> inHand) {
            if (click == MenuClick.OTHER) {
                return;
            }
            if (slot == BACK) {
                navigation.home();
                return;
            }
            if (slot >= 45 || slot >= category.settings().size()) {
                return;
            }
            Setting setting = category.settings().get(slot);
            switch (setting.kind()) {
                case BOOLEAN -> draft.set(setting.path(), !draft.bool(setting.path()));
                case CHOICE -> draft.cycle(setting, click == MenuClick.RIGHT ? -1 : 1);
                case INTEGER, DECIMAL -> {
                    if (click == MenuClick.SHIFT) {
                        navigation.ask(setting);
                    } else {
                        draft.step(setting, click == MenuClick.RIGHT ? -1 : 1);
                    }
                }
                case SECRET -> {
                    if (navigation.secretsAllowed()) {
                        navigation.ask(setting);
                    } else {
                        navigation.secretDenied();
                    }
                }
                case TEXT, TEXT_LIST, TEXT_MAP -> navigation.ask(setting);
                case TYPES, PRIORITY, ROLES -> navigation.special(setting);
            }
        }

        private String display(Language text, Setting setting) {
            String path = setting.path();
            return switch (setting.kind()) {
                case BOOLEAN -> text.text(draft.bool(path) ? "settings.yes-label" : "settings.no-label");
                case SECRET -> text.text(draft.text(path).isEmpty() ? "settings.secret-empty" : "settings.secret-set");
                case INTEGER, DECIMAL -> draft.number(path).stripTrailingZeros().toPlainString();
                case TEXT_LIST, TYPES, PRIORITY -> draft.list(path).isEmpty() ? text.text("settings.empty")
                        : String.join(", ", draft.list(path));
                case TEXT_MAP -> draft.map(path).isEmpty() ? text.text("settings.empty")
                        : String.join("; ", draft.map(path).entrySet().stream().map(e -> e.getKey() + "=" + e.getValue())
                        .toList());
                case ROLES -> text.text("settings.roles-summary", "count", draft.types().size());
                default -> draft.text(path).isEmpty() ? text.text("settings.empty") : draft.text(path);
            };
        }

        private static String hint(Setting setting) {
            return switch (setting.kind()) {
                case BOOLEAN -> "toggle";
                case CHOICE -> "choice";
                case INTEGER, DECIMAL -> "number";
                case TYPES, PRIORITY, ROLES -> "open";
                default -> "type";
            };
        }
    }

    // ------------------------------------------------------------------ types de banques et ordre de choix

    /** Types de banques (actives ou non) ou ordre de choix par defaut : une case par type, clic = basculer. */
    public static final class Types implements EditorScreen {

        private final Supplier<Language> language;
        private final SettingsDraft draft;
        private final Supplier<List<String>> types;
        private final boolean priority;
        private final Runnable back;

        /**
         * @param language langue en vigueur
         * @param draft    brouillon
         * @param types    types proposes (types connus du serveur, ou types actifs pour l'ordre de choix)
         * @param priority {@code true} pour l'ordre de choix par defaut, {@code false} pour les types actifs
         * @param back     retour a la page des banques
         */
        public Types(Supplier<Language> language, SettingsDraft draft, Supplier<List<String>> types, boolean priority,
                     Runnable back) {
            this.language = Objects.requireNonNull(language, "language");
            this.draft = Objects.requireNonNull(draft, "draft");
            this.types = Objects.requireNonNull(types, "types");
            this.priority = priority;
            this.back = Objects.requireNonNull(back, "back");
        }

        @Override
        public String title() {
            return language.get().text(priority ? "settings.priority.title" : "settings.types.title");
        }

        @Override
        public int size() {
            return 54;
        }

        @Override
        public Optional<SlotView> slot(int slot) {
            Language text = language.get();
            if (slot == BACK) {
                return Optional.of(new SlotView(BACK_ICON, text.text("settings.back"), List.of()));
            }
            List<String> shown = types.get();
            if (slot >= 45 || slot >= shown.size()) {
                return Optional.empty();
            }
            String type = shown.get(slot);
            String state;
            if (priority) {
                int rank = draft.priority().indexOf(type);
                state = rank < 0 ? text.text("settings.priority.out") : text.text("settings.priority.rank", "rank", rank + 1);
            } else {
                state = text.text(draft.types().contains(type) ? "settings.types.active" : "settings.types.inactive");
            }
            return Optional.of(new SlotView(ItemKey.minecraft(TYPE_ICONS.getOrDefault(type, "chest")),
                    text.text("settings.types.name", "type", type),
                    SlotView.lines(state + "\n" + text.text(priority ? "settings.priority.hint" : "settings.types.hint"))));
        }

        @Override
        public void click(int slot, MenuClick click, Optional<ItemKey> inHand) {
            if (click == MenuClick.OTHER) {
                return;
            }
            if (slot == BACK) {
                back.run();
                return;
            }
            List<String> shown = types.get();
            if (slot < 45 && slot < shown.size()) {
                if (priority) {
                    draft.togglePriority(shown.get(slot));
                } else {
                    draft.toggleType(shown.get(slot));
                }
            }
        }
    }

    // ------------------------------------------------------------------ droits des roles

    /**
     * Droits des roles, 5 types actifs par page : colonne 0 le type, colonnes 1 a 3 le depot (owner, admin, member),
     * colonnes 5 a 7 le retrait. Clic sur une case = autoriser / interdire.
     */
    public static final class Roles implements EditorScreen {

        static final int TYPES_PER_PAGE = 5;
        private static final ItemKey ALLOWED = ItemKey.minecraft("lime_dye");
        private static final ItemKey DENIED = ItemKey.minecraft("gray_dye");

        private final Supplier<Language> language;
        private final SettingsDraft draft;
        private final Runnable back;
        private int page;

        /**
         * @param language langue en vigueur
         * @param draft    brouillon
         * @param back     retour a la page des banques
         */
        public Roles(Supplier<Language> language, SettingsDraft draft, Runnable back) {
            this.language = Objects.requireNonNull(language, "language");
            this.draft = Objects.requireNonNull(draft, "draft");
            this.back = Objects.requireNonNull(back, "back");
        }

        private int pageCount() {
            return Math.max(1, (draft.types().size() + TYPES_PER_PAGE - 1) / TYPES_PER_PAGE);
        }

        @Override
        public String title() {
            return language.get().text("settings.roles.title");
        }

        @Override
        public int size() {
            return 54;
        }

        @Override
        public Optional<SlotView> slot(int slot) {
            Language text = language.get();
            if (slot == BACK) {
                return Optional.of(new SlotView(BACK_ICON, text.text("settings.back"), List.of()));
            }
            if (slot == PREVIOUS && page > 0) {
                return Optional.of(new SlotView(BACK_ICON, text.text("settings.previous"), List.of()));
            }
            if (slot == NEXT && page < pageCount() - 1) {
                return Optional.of(new SlotView(BACK_ICON, text.text("settings.next"), List.of()));
            }
            Optional<String> type = typeAt(slot);
            if (type.isEmpty() || slot >= 45) {
                return Optional.empty();
            }
            int column = slot % 9;
            if (column == 0) {
                return Optional.of(new SlotView(ItemKey.minecraft(TYPE_ICONS.getOrDefault(type.get(), "chest")),
                        text.text("settings.types.name", "type", type.get()), SlotView.lines(text.text("settings.roles.legend"))));
            }
            Optional<Cell> cell = cell(column);
            if (cell.isEmpty()) {
                return Optional.empty();
            }
            boolean allowed = draft.roles(type.get(), cell.get().withdraw()).contains(cell.get().role());
            return Optional.of(new SlotView(allowed ? ALLOWED : DENIED,
                    text.text(cell.get().withdraw() ? "settings.roles.withdraw" : "settings.roles.deposit", "role",
                            cell.get().role(), "type", type.get()),
                    List.of(text.text(allowed ? "settings.roles.allowed" : "settings.roles.denied"),
                            text.text("settings.roles.hint"))));
        }

        @Override
        public void click(int slot, MenuClick click, Optional<ItemKey> inHand) {
            if (click == MenuClick.OTHER) {
                return;
            }
            if (slot == BACK) {
                back.run();
            } else if (slot == PREVIOUS && page > 0) {
                page--;
            } else if (slot == NEXT && page < pageCount() - 1) {
                page++;
            } else if (slot < 45) {
                Optional<String> type = typeAt(slot);
                Optional<Cell> cell = cell(slot % 9);
                if (type.isPresent() && cell.isPresent()) {
                    draft.toggleRole(type.get(), cell.get().withdraw(), cell.get().role());
                }
            }
        }

        private Optional<String> typeAt(int slot) {
            int index = page * TYPES_PER_PAGE + slot / 9;
            List<String> types = draft.types();
            return slot / 9 < TYPES_PER_PAGE && index < types.size() ? Optional.of(types.get(index)) : Optional.empty();
        }

        private record Cell(boolean withdraw, String role) {
        }

        private static Optional<Cell> cell(int column) {
            if (column >= 1 && column <= 3) {
                return Optional.of(new Cell(false, SettingsDraft.ROLES.get(column - 1)));
            }
            if (column >= 5 && column <= 7) {
                return Optional.of(new Cell(true, SettingsDraft.ROLES.get(column - 5)));
            }
            return Optional.empty();
        }
    }

    // ------------------------------------------------------------------ outils

    private static boolean changedIn(SettingsDraft draft, Setting setting) {
        if (setting.kind() == Setting.Kind.ROLES) {
            return draft.types().stream().anyMatch(type -> draft.changed("banks.roles." + type + ".deposit")
                    || draft.changed("banks.roles." + type + ".withdraw"));
        }
        return draft.changed(setting.path());
    }

    /**
     * Lit une saisie au clavier pour un reglage.
     *
     * @param setting reglage
     * @param input   ligne tapee (sans espaces autour) ; {@code -} = vide
     * @return la valeur normalisee, ou vide si la saisie est invalide (nombre hors bornes...)
     */
    public static Optional<Object> parse(Setting setting, String input) {
        String text = input.equals("-") ? "" : input;
        try {
            return switch (setting.kind()) {
                case INTEGER -> {
                    long value = Long.parseLong(text.replace(" ", ""));
                    yield value < setting.min() || value > setting.max() ? Optional.empty() : Optional.of(value);
                }
                case DECIMAL -> {
                    BigDecimal value = new BigDecimal(text.replace(',', '.'));
                    yield value.signum() < 0 ? Optional.empty() : Optional.of(value);
                }
                case TEXT_LIST -> Optional.of(Arrays.stream(text.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                        .toList());
                case TEXT_MAP -> {
                    Map<String, String> map = new java.util.TreeMap<>();
                    for (String entry : text.split(";")) {
                        if (entry.isBlank()) {
                            continue;
                        }
                        int equals = entry.indexOf('=');
                        String key = equals <= 0 ? "" : entry.substring(0, equals).trim();
                        // La cle est ecrite sans guillemets dans config.yml : caracteres simples seulement.
                        if (!OPTION_KEY.matcher(key).matches()) {
                            yield Optional.empty();
                        }
                        map.put(key, entry.substring(equals + 1).trim());
                    }
                    yield Optional.of(map);
                }
                case TEXT, SECRET -> Optional.of(text);
                default -> Optional.empty();
            };
        } catch (NumberFormatException invalid) {
            return Optional.empty();
        }
    }
}
