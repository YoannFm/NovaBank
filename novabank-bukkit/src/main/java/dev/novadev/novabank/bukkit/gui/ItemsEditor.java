package dev.novadev.novabank.bukkit.gui;

import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.bukkit.message.Language;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Editeur des items acceptes et de leurs prix : un brouillon de la liste {@code items:} du fichier. Il part de la liste
 * BRUTE du fichier (et non de la configuration chargee) pour ne jamais perdre un item que ce serveur ignore, faute du
 * plugin qui le fournit. Coffre pagine : clic gauche +1 au prix, clic droit -1, clic avec majuscule retire l'item ; la barre du bas
 * change de page et ajoute l'item tenu en main. Les memes operations existent en commandes. Rien n'est enregistre avant
 * {@link #toBlock()} (commande {@code /novabank items save}).
 */
public final class ItemsEditor implements EditorScreen {

    /** Une ligne de la liste. */
    public record Entry(ItemKey key, BigDecimal price, String category, String displayName) {
    }

    /** Cases de stock d'une page (les cinq premieres lignes). */
    static final int PAGE_SIZE = 45;

    private static final Pattern CATEGORY = Pattern.compile("[a-z0-9_-]+");
    private static final ItemKey ARROW = ItemKey.minecraft("arrow");
    private static final ItemKey BOOK = ItemKey.minecraft("book");
    private static final ItemKey HOPPER = ItemKey.minecraft("hopper");

    private final Supplier<Language> language;
    private final List<Entry> original;
    private final List<Entry> entries;
    private int page;
    private Consumer<ItemKey> onSelect;

    /**
     * @param rawItems liste {@code items} lue dans le fichier (elements : tables cle/prix/categorie/nom)
     * @param language langue en vigueur
     */
    public ItemsEditor(List<?> rawItems, Supplier<Language> language) {
        this.language = Objects.requireNonNull(language, "language");
        List<Entry> read = new ArrayList<>();
        for (Object raw : rawItems == null ? List.of() : rawItems) {
            if (raw instanceof Map<?, ?> map) {
                try {
                    read.add(new Entry(ItemKey.parse(String.valueOf(map.get("key"))),
                            new BigDecimal(String.valueOf(map.get("price"))), text(map.get("category")),
                            text(map.get("display-name"))));
                } catch (IllegalArgumentException invalid) {
                    // Entree illisible : la validation du fichier l'a deja signalee, elle n'est pas reprise.
                }
            }
        }
        this.original = List.copyOf(read);
        this.entries = new ArrayList<>(read);
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /** @param handler appele au clic gauche ou droit sur un item (ecran de prix) ; sans lui, le clic ajuste le prix de 1 */
    public void onSelect(Consumer<ItemKey> handler) {
        this.onSelect = handler;
    }

    /**
     * @param key item
     * @return sa ligne du brouillon
     */
    public Optional<Entry> find(ItemKey key) {
        int index = indexOf(key);
        return index < 0 ? Optional.empty() : Optional.of(entries.get(index));
    }

    /** @return les entrees du brouillon */
    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    /** @return {@code true} si le brouillon differe du fichier */
    public boolean dirty() {
        return !original.equals(entries);
    }

    /**
     * Ajoute un item, ou remplace son prix et sa categorie s'il existe deja.
     *
     * @param key      item
     * @param price    prix unitaire (positif ou nul)
     * @param category categorie, ou {@code null}
     * @return {@code false} si le prix est negatif ou la categorie invalide
     */
    public boolean add(ItemKey key, BigDecimal price, String category) {
        if (price.signum() < 0 || category != null && !CATEGORY.matcher(category).matches()) {
            return false;
        }
        int index = indexOf(key);
        if (index < 0) {
            entries.add(new Entry(key, price, category, null));
        } else {
            Entry old = entries.get(index);
            entries.set(index, new Entry(key, price, category == null ? old.category() : category, old.displayName()));
        }
        return true;
    }

    /**
     * @param key   item present
     * @param price nouveau prix (positif ou nul)
     * @return {@code false} si l'item est absent ou le prix negatif
     */
    public boolean setPrice(ItemKey key, BigDecimal price) {
        int index = indexOf(key);
        if (index < 0 || price.signum() < 0) {
            return false;
        }
        Entry old = entries.get(index);
        entries.set(index, new Entry(key, price, old.category(), old.displayName()));
        return true;
    }

    /**
     * @param key      item present
     * @param category categorie, ou {@code null} pour la retirer
     * @return {@code false} si l'item est absent ou la categorie invalide
     */
    public boolean setCategory(ItemKey key, String category) {
        int index = indexOf(key);
        if (index < 0 || category != null && !CATEGORY.matcher(category).matches()) {
            return false;
        }
        Entry old = entries.get(index);
        entries.set(index, new Entry(key, old.price(), category, old.displayName()));
        return true;
    }

    /**
     * @param key  item present
     * @param name nom affiche (codes couleur admis), ou {@code null} pour le retirer
     * @return {@code false} si l'item est absent ou le nom vide
     */
    public boolean setDisplayName(ItemKey key, String name) {
        int index = indexOf(key);
        if (index < 0 || name != null && name.isBlank()) {
            return false;
        }
        Entry old = entries.get(index);
        entries.set(index, new Entry(key, old.price(), old.category(), name));
        return true;
    }

    /**
     * @param key item
     * @return {@code true} s'il etait present
     */
    public boolean remove(ItemKey key) {
        int index = indexOf(key);
        if (index >= 0) {
            entries.remove(index);
            page = Math.min(page, Math.max(0, pageCount() - 1));
        }
        return index >= 0;
    }

    @Override
    public String title() {
        return language.get().text("editor.items.title", "page", page + 1, "pages", pageCount());
    }

    @Override
    public int size() {
        return 54;
    }

    @Override
    public Optional<SlotView> slot(int slot) {
        Language text = language.get();
        if (slot < PAGE_SIZE) {
            int index = page * PAGE_SIZE + slot;
            if (index >= entries.size()) {
                return Optional.empty();
            }
            Entry entry = entries.get(index);
            String name = entry.displayName() != null ? entry.displayName() : entry.key().toString();
            return Optional.of(new SlotView(entry.key(), text.text("editor.items.entry-name", "item", name),
                    SlotView.lines(text.text("editor.items.entry-lore", "price", entry.price().stripTrailingZeros().toPlainString(),
                            "category", entry.category() == null ? "-" : entry.category()))));
        }
        return switch (slot) {
            case 45 -> page > 0 ? Optional.of(new SlotView(ARROW, text.text("editor.items.previous"), List.of()))
                    : Optional.empty();
            case 46 -> Optional.of(new SlotView(BOOK, text.text("editor.items.info-name"), SlotView.lines(text.text(
                    dirty() ? "editor.items.info-dirty" : "editor.items.info-clean", "count", entries.size()))));
            case 49 -> Optional.of(new SlotView(HOPPER, text.text("editor.items.add-name"),
                    SlotView.lines(text.text("editor.items.add-lore"))));
            case 53 -> page < pageCount() - 1
                    ? Optional.of(new SlotView(ARROW, text.text("editor.items.next"), List.of())) : Optional.empty();
            default -> Optional.empty();
        };
    }

    @Override
    public void click(int slot, MenuClick click, Optional<ItemKey> inHand) {
        if (slot >= 0 && slot < PAGE_SIZE) {
            int index = page * PAGE_SIZE + slot;
            if (index >= entries.size()) {
                return;
            }
            Entry entry = entries.get(index);
            if (click == MenuClick.SHIFT) {
                remove(entry.key());
            } else if ((click == MenuClick.LEFT || click == MenuClick.RIGHT) && onSelect != null) {
                onSelect.accept(entry.key());
            } else if (click == MenuClick.LEFT || click == MenuClick.RIGHT) {
                BigDecimal step = click == MenuClick.LEFT ? BigDecimal.ONE : BigDecimal.ONE.negate();
                setPrice(entry.key(), entry.price().add(step).max(BigDecimal.ZERO));
            }
        } else if (click == MenuClick.LEFT) {
            if (slot == 45 && page > 0) {
                page--;
            } else if (slot == 53 && page < pageCount() - 1) {
                page++;
            } else if (slot == 49 && inHand.isPresent() && indexOf(inHand.get()) < 0) {
                add(inHand.get(), BigDecimal.ONE, null);
            }
        }
    }

    @Override
    public void clickPlayerItem(ItemKey item, MenuClick click) {
        if (click != MenuClick.LEFT && click != MenuClick.RIGHT) {
            return;
        }
        if (indexOf(item) < 0) {
            add(item, BigDecimal.ONE, null);
            page = pageCount() - 1;
        } else if (onSelect != null) {
            onSelect.accept(item);
        }
    }

    /** @return la section {@code items:} du brouillon, en texte, prete a etre ecrite dans {@code config.yml} */
    public String toBlock() {
        if (entries.isEmpty()) {
            return "items: []";
        }
        StringBuilder text = new StringBuilder("items:");
        for (Entry entry : entries) {
            text.append("\n  - key: ").append(entry.key());
            text.append("\n    price: \"").append(entry.price().stripTrailingZeros().toPlainString()).append('"');
            if (entry.category() != null) {
                text.append("\n    category: ").append(entry.category());
            }
            if (entry.displayName() != null) {
                text.append("\n    display-name: ").append(quote(entry.displayName()));
            }
        }
        return text.toString();
    }

    /** @return une ligne par item du brouillon : {@code cle = prix [categorie]} */
    public Map<String, String> summary() {
        Map<String, String> lines = new LinkedHashMap<>();
        entries.forEach(entry -> lines.put(entry.key().toString(), entry.price().stripTrailingZeros().toPlainString()
                + (entry.category() == null ? "" : " [" + entry.category() + "]")));
        return lines;
    }

    private int pageCount() {
        return Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private int indexOf(ItemKey key) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).key().equals(key)) {
                return i;
            }
        }
        return -1;
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
