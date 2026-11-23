package dev.novadev.novabank.bukkit.gui;

import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.bukkit.message.Language;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Fiche d'un item du brouillon : prix par vitres (-50 / -0,50 / +0,50 / +50) ou au clavier (panneau), categorie (coffre)
 * et nom affiche (etiquette) au clavier, suppression (emeraudes) et retour a la liste (barriere). Les prix restent
 * positifs ou nuls.
 */
public final class ItemPriceScreen implements EditorScreen {

    /** Valeur saisie au clavier. */
    public enum Field { PRICE, CATEGORY, NAME }

    static final int MINUS_BIG = 10;
    static final int MINUS_SMALL = 11;
    static final int ITEM = 13;
    static final int PLUS_SMALL = 15;
    static final int PLUS_BIG = 16;
    static final int CATEGORY = 20;
    static final int TYPE = 22;
    static final int NAME = 24;
    static final int DELETE_A = 27;
    static final int DELETE_B = 35;
    static final int BACK = 31;

    private static final Map<Integer, BigDecimal> STEPS = Map.of(MINUS_BIG, new BigDecimal("-50"),
            MINUS_SMALL, new BigDecimal("-0.50"), PLUS_SMALL, new BigDecimal("0.50"), PLUS_BIG, new BigDecimal("50"));

    private final Supplier<Language> language;
    private final ItemsEditor items;
    private final ItemKey key;
    private final Runnable back;
    private final Consumer<Field> ask;

    /**
     * @param language langue en vigueur
     * @param items    brouillon d'items
     * @param key      item edite
     * @param back     retour a la liste
     * @param ask      demande la saisie au clavier d'une valeur
     */
    public ItemPriceScreen(Supplier<Language> language, ItemsEditor items, ItemKey key, Runnable back, Consumer<Field> ask) {
        this.language = Objects.requireNonNull(language, "language");
        this.items = Objects.requireNonNull(items, "items");
        this.key = Objects.requireNonNull(key, "key");
        this.back = Objects.requireNonNull(back, "back");
        this.ask = Objects.requireNonNull(ask, "ask");
    }

    @Override
    public String title() {
        return language.get().text("editor.price.title");
    }

    @Override
    public int size() {
        return 36;
    }

    @Override
    public Optional<SlotView> slot(int slot) {
        Language text = language.get();
        Optional<ItemsEditor.Entry> entry = items.find(key);
        String price = entry.map(e -> e.price().stripTrailingZeros().toPlainString()).orElse("-");
        String category = entry.map(ItemsEditor.Entry::category).orElse(null);
        String name = entry.map(ItemsEditor.Entry::displayName).orElse(null);
        return switch (slot) {
            case MINUS_BIG, MINUS_SMALL, PLUS_SMALL, PLUS_BIG -> {
                BigDecimal step = STEPS.get(slot);
                String amount = step.abs().stripTrailingZeros().toPlainString();
                yield Optional.of(new SlotView(ItemKey.minecraft(step.signum() < 0 ? "red_stained_glass_pane"
                        : "green_stained_glass_pane"), text.text(step.signum() < 0 ? "editor.price.minus" : "editor.price.plus",
                        "amount", amount), List.of()));
            }
            case ITEM -> Optional.of(new SlotView(key, text.text("editor.price.item-name", "item", name != null ? name
                    : key.toString()), SlotView.lines(text.text("editor.price.item-lore", "price", price, "category",
                    category == null ? "-" : category, "id", key.toString()))));
            case CATEGORY -> Optional.of(new SlotView(ItemKey.minecraft("chest"), text.text("editor.price.category-name"),
                    SlotView.lines(text.text("editor.price.category-lore", "category", category == null ? "-" : category))));
            case NAME -> Optional.of(new SlotView(ItemKey.minecraft("name_tag"), text.text("editor.price.name-name"),
                    SlotView.lines(text.text("editor.price.name-lore", "name", name == null ? "-" : name))));
            case TYPE -> Optional.of(new SlotView(ItemKey.minecraft("oak_sign"), text.text("editor.price.type-name"),
                    List.of(text.text("editor.price.type-lore"))));
            case DELETE_A, DELETE_B -> Optional.of(new SlotView(ItemKey.minecraft("emerald"),
                    text.text("editor.price.delete-name"), List.of(text.text("editor.price.delete-lore"))));
            case BACK -> Optional.of(new SlotView(ItemKey.minecraft("barrier"), text.text("editor.price.back"), List.of()));
            default -> Optional.empty();
        };
    }

    @Override
    public void click(int slot, MenuClick click, Optional<ItemKey> inHand) {
        if (click != MenuClick.LEFT && click != MenuClick.RIGHT) {
            return;
        }
        if (STEPS.containsKey(slot)) {
            items.find(key).ifPresent(entry -> items.setPrice(key, entry.price().add(STEPS.get(slot)).max(BigDecimal.ZERO)));
        } else if (slot == TYPE) {
            ask.accept(Field.PRICE);
        } else if (slot == CATEGORY) {
            ask.accept(Field.CATEGORY);
        } else if (slot == NAME) {
            ask.accept(Field.NAME);
        } else if (slot == DELETE_A || slot == DELETE_B) {
            items.remove(key);
            back.run();
        } else if (slot == BACK) {
            back.run();
        }
    }
}
