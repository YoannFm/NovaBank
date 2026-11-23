package dev.novadev.novabank.bukkit.gui;

import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.bukkit.item.ItemProvider;
import dev.novadev.novabank.bukkit.item.ItemProviders;
import dev.novadev.novabank.bukkit.message.Language;
import dev.novadev.novabank.config.model.GuiButton;
import dev.novadev.novabank.config.model.GuiSettings;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import org.bukkit.inventory.ItemStack;

/**
 * Contenu du coffre : icones du stock (item, quantite et valeur dans la description),
 * boutons et remplissage, textes de la langue en vigueur. Thread du joueur.
 */
public final class MenuRenderer {

    private static final ItemKey UNKNOWN_ICON = ItemKey.minecraft("barrier");

    private final Supplier<Language> language;
    private final Function<ItemKey, String> itemNames;
    private final Function<EntityRef, String> bankNames;
    private final ItemProviders providers;

    /**
     * @param language  langue en vigueur
     * @param itemNames nom affiche d'un item
     * @param bankNames nom affiche d'une banque
     * @param providers fournisseurs d'items (icones)
     */
    public MenuRenderer(Supplier<Language> language, Function<ItemKey, String> itemNames,
                        Function<EntityRef, String> bankNames, ItemProviders providers) {
        this.language = Objects.requireNonNull(language, "language");
        this.itemNames = Objects.requireNonNull(itemNames, "itemNames");
        this.bankNames = Objects.requireNonNull(bankNames, "bankNames");
        this.providers = Objects.requireNonNull(providers, "providers");
    }

    /**
     * @param model contenu
     * @return titre du coffre
     */
    public String title(MenuModel model) {
        return language.get().text("gui.title", "bank", bankNames.apply(model.bank()));
    }

    /**
     * @param model    contenu
     * @param layout   disposition
     * @param settings icones des boutons et remplissage
     * @return le contenu de chaque case du coffre
     */
    public ItemStack[] render(MenuModel model, MenuLayout layout, GuiSettings settings) {
        Language text = language.get();
        ItemStack[] contents = new ItemStack[layout.size()];
        List<StockEntry> entries = model.page().entries();
        for (int index = 0; index < entries.size(); index++) {
            contents[layout.stockSlot(index)] = stockIcon(text, entries.get(index));
        }
        settings.filler().flatMap(filler -> icon(filler, 1)).ifPresent(filler -> layout.fillerSlots()
                .forEach(slot -> contents[slot] = ItemTexts.describe(filler.clone(), " ", List.of())));
        for (GuiButton button : GuiButton.values()) {
            if (visible(button, model.page())) {
                contents[layout.slot(button)] = buttonIcon(text, button, settings, model);
            }
        }
        return contents;
    }

    /**
     * @param item item sans place dans l'inventaire
     * @return message au joueur
     */
    public List<String> noRoom(ItemKey item) {
        return List.of(language.get().chat("gui.no-room", "item", itemNames.apply(item)));
    }

    private ItemStack stockIcon(Language text, StockEntry entry) {
        int maxStack = providers.provider(entry.item()).map(provider -> provider.maxStackSize(entry.item())).orElse(1);
        int shown = (int) Math.max(1, Math.min(entry.quantity(), maxStack));
        ItemStack icon = icon(entry.item(), shown).or(() -> icon(UNKNOWN_ICON, 1)).orElseThrow();
        return ItemTexts.describe(icon, text.text("gui.stock.name", "item", itemNames.apply(entry.item())),
                SlotView.lines(text.text("gui.stock.lore", "quantity", text.quantity(entry.quantity()), "value",
                        text.value(entry.value()), "stack", maxStack)));
    }

    private ItemStack buttonIcon(Language text, GuiButton button, GuiSettings settings, MenuModel model) {
        Object[] placeholders = {"bank", bankNames.apply(model.bank()), "value", text.value(model.value()), "page",
                model.page().page() + 1, "pages", model.page().pageCount()};
        String prefix = "gui.buttons." + button.key();
        ItemStack icon = icon(settings.buttons().get(button).icon(), 1).or(() -> icon(UNKNOWN_ICON, 1)).orElseThrow();
        return ItemTexts.describe(icon, text.text(prefix + ".name", placeholders),
                SlotView.lines(text.text(prefix + ".lore", placeholders)));
    }

    private static boolean visible(GuiButton button, StockPage page) {
        return switch (button) {
            case PREVIOUS -> page.hasPrevious();
            case NEXT -> page.hasNext();
            default -> true;
        };
    }

    private Optional<ItemStack> icon(ItemKey key, int amount) {
        Optional<ItemProvider> provider = providers.provider(key);
        return provider.isEmpty() ? Optional.empty() : provider.get().create(key, amount);
    }
}
