package dev.novadev.novabank.bukkit.gui;

import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.bukkit.message.Language;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Liste des items acceptes et de ce qu'ils rapportent, ouverte par le bouton Information du menu de banque. Lecture
 * seule, 45 items par page ; barre du bas : page precedente (45), retour a la banque (49), page suivante (53). Sans
 * dependance serveur ; affiche par {@link EditorMenus}.
 */
public final class PriceListScreen implements EditorScreen {

    static final int PAGE_SIZE = 45;
    static final int PREVIOUS = 45;
    static final int BACK = 49;
    static final int NEXT = 53;

    private static final ItemKey ARROW = ItemKey.minecraft("arrow");
    private static final ItemKey DOOR = ItemKey.minecraft("oak_door");

    /**
     * Un item accepte.
     *
     * @param key      cle
     * @param name     nom affiche
     * @param price    prix unitaire
     * @param category categorie, {@code null} si aucune
     */
    public record Entry(ItemKey key, String name, BigDecimal price, String category) {

        /**
         * @throws NullPointerException si la cle, le nom ou le prix est nul
         */
        public Entry {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(price, "price");
        }
    }

    private final Supplier<Language> language;
    private final List<Entry> entries;
    private final Runnable back;
    private int page;

    /**
     * @param language langue en vigueur
     * @param entries  items acceptes, dans l'ordre du catalogue
     * @param back     retour au menu de banque
     */
    public PriceListScreen(Supplier<Language> language, List<Entry> entries, Runnable back) {
        this.language = Objects.requireNonNull(language, "language");
        this.entries = List.copyOf(entries);
        this.back = Objects.requireNonNull(back, "back");
    }

    private int pageCount() {
        return Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    @Override
    public String title() {
        return language.get().text("gui.prices.title"); // un titre de coffre ne change pas en tournant la page
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
            String lore = text.text(entry.price().signum() > 0 ? "gui.prices.entry-lore" : "gui.prices.entry-lore-zero",
                    "price", text.value(entry.price()), "category", entry.category() == null ? "-" : entry.category());
            return Optional.of(new SlotView(entry.key(), text.text("gui.prices.entry-name", "item", entry.name()),
                    SlotView.lines(lore)));
        }
        return switch (slot) {
            case PREVIOUS -> page > 0 ? Optional.of(new SlotView(ARROW, text.text("gui.prices.previous"), List.of()))
                    : Optional.empty();
            case BACK -> Optional.of(new SlotView(DOOR, text.text("gui.prices.back"), List.of()));
            case NEXT -> page < pageCount() - 1
                    ? Optional.of(new SlotView(ARROW, text.text("gui.prices.next"), List.of())) : Optional.empty();
            default -> Optional.empty();
        };
    }

    @Override
    public void click(int slot, MenuClick click, Optional<ItemKey> inHand) {
        if (click != MenuClick.LEFT && click != MenuClick.RIGHT) {
            return;
        }
        if (slot == PREVIOUS && page > 0) {
            page--;
        } else if (slot == NEXT && page < pageCount() - 1) {
            page++;
        } else if (slot == BACK) {
            back.run();
        }
    }
}
