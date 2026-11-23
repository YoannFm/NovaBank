package dev.novadev.novabank.bukkit.gui;

import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.bukkit.message.Language;
import dev.novadev.novabank.config.model.GuiButton;
import dev.novadev.novabank.config.model.GuiButtonSettings;
import dev.novadev.novabank.config.model.GuiSettings;
import dev.novadev.novabank.config.section.GuiSectionParser;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Editeur visuel de la disposition du GUI de banque : un brouillon (lignes, case et icone de chaque bouton) qui se modifie
 * au clic. Clic gauche : prendre un bouton puis le poser sur une case (echange si elle en porte un). Clic droit sur un
 * bouton : son icone devient l'item tenu en main. Rien n'est enregistre avant {@link #toBlock()} (commande
 * {@code /novabank gui save}).
 */
public final class GuiEditor implements EditorScreen {

    private static final ItemKey STOCK_ICON = ItemKey.minecraft("light_blue_stained_glass_pane");
    private static final ItemKey FREE_ICON = ItemKey.minecraft("white_stained_glass_pane");

    private final Supplier<Language> language;
    private final GuiSettings original;
    private int rows;
    private final Map<GuiButton, Integer> slots = new EnumMap<>(GuiButton.class);
    private final Map<GuiButton, ItemKey> icons = new EnumMap<>(GuiButton.class);
    private final Optional<ItemKey> filler;
    private GuiButton selected;

    /**
     * @param current  disposition en vigueur, point de depart du brouillon
     * @param language langue en vigueur
     */
    public GuiEditor(GuiSettings current, Supplier<Language> language) {
        this.original = Objects.requireNonNull(current, "current");
        this.language = Objects.requireNonNull(language, "language");
        this.rows = current.rows();
        this.filler = current.filler();
        current.buttons().forEach((button, settings) -> {
            slots.put(button, settings.slot());
            icons.put(button, settings.icon());
        });
    }

    @Override
    public String title() {
        return language.get().text("editor.gui.title");
    }

    @Override
    public int size() {
        return rows * GuiSettings.COLUMNS;
    }

    /** @return nombre de lignes du brouillon */
    public int rows() {
        return rows;
    }

    /** @return {@code true} si le brouillon differe de la disposition en vigueur */
    public boolean dirty() {
        return !toSettings().equals(original);
    }

    @Override
    public Optional<SlotView> slot(int slot) {
        Language text = language.get();
        GuiButton button = buttonAt(slot);
        if (button != null) {
            String name = text.text("editor.gui.button-name", "button", text.text("editor.gui.buttons." + button.key()),
                    "selected", button == selected ? text.text("editor.gui.selected") : "");
            return Optional.of(new SlotView(icons.get(button), name, SlotView.lines(text.text("editor.gui.button-lore", "slot",
                    slot))));
        }
        if (slot < (rows - 1) * GuiSettings.COLUMNS) {
            return Optional.of(new SlotView(STOCK_ICON, text.text("editor.gui.stock-name"),
                    SlotView.lines(text.text("editor.gui.stock-lore"))));
        }
        if (selected != null) {
            return Optional.of(new SlotView(FREE_ICON, text.text("editor.gui.free-name"),
                    SlotView.lines(text.text("editor.gui.free-lore"))));
        }
        return filler.map(icon -> new SlotView(icon, " ", List.of()));
    }

    @Override
    public void click(int slot, MenuClick click, Optional<ItemKey> inHand) {
        if (slot < 0 || slot >= size()) {
            return;
        }
        GuiButton clicked = buttonAt(slot);
        if (click == MenuClick.RIGHT && clicked != null && inHand.isPresent()
                && ItemKey.MINECRAFT.equals(inHand.get().namespace())) {
            icons.put(clicked, inHand.get());
        } else if (click == MenuClick.LEFT) {
            if (selected == null) {
                selected = clicked;
            } else if (clicked == selected) {
                selected = null;
            } else {
                int from = slots.get(selected);
                slots.put(selected, slot);
                if (clicked != null) {
                    slots.put(clicked, from);
                }
                selected = null;
            }
        }
    }

    /**
     * Change le nombre de lignes ; un bouton qui sortirait du coffre est replace dans la derniere ligne.
     *
     * @param newRows lignes (2 a 6)
     * @return {@code false} si le nombre est hors bornes
     */
    public boolean setRows(int newRows) {
        if (newRows < GuiSettings.MIN_ROWS || newRows > GuiSettings.MAX_ROWS) {
            return false;
        }
        rows = newRows;
        selected = null;
        int bar = (rows - 1) * GuiSettings.COLUMNS;
        for (GuiButton button : GuiButton.values()) {
            if (slots.get(button) >= size()) {
                int free = bar;
                while (buttonAt(free) != null) {
                    free++;
                }
                slots.put(button, free);
            }
        }
        return true;
    }

    /** Remet la disposition par defaut (boutons dans la derniere ligne, icones d'origine). */
    public void reset() {
        selected = null;
        int bar = (rows - 1) * GuiSettings.COLUMNS;
        GuiSectionParser.defaultButtons().forEach((button, defaults) -> {
            slots.put(button, bar + defaults.slot());
            icons.put(button, defaults.icon());
        });
    }

    /** @return la section {@code gui:} du brouillon, en texte, prete a etre ecrite dans {@code config.yml} */
    public String toBlock() {
        StringBuilder text = new StringBuilder("gui:\n");
        text.append("  # Nombre de lignes du coffre (2 a 6) : la derniere est la barre ; le stock occupe les cases libres des autres.\n");
        text.append("  rows: ").append(rows).append('\n');
        text.append("  # Boutons : case du coffre (\"slot\", 0 a 53 depuis le haut a gauche) et icone (item minecraft:...).\n");
        text.append("  # Editeur visuel en jeu : /novabank gui.\n");
        text.append("  buttons:\n");
        for (GuiButton button : GuiButton.values()) {
            text.append("    ").append(button.key()).append(":\n");
            text.append("      slot: ").append(slots.get(button)).append('\n');
            text.append("      icon: ").append(icons.get(button)).append('\n');
        }
        text.append("  # Item des cases vides de la derniere ligne (\"\" : cases laissees vides).\n");
        text.append("  filler: ").append(filler.map(ItemKey::toString).orElse("\"\""));
        return text.toString();
    }

    private GuiSettings toSettings() {
        Map<GuiButton, GuiButtonSettings> buttons = new EnumMap<>(GuiButton.class);
        slots.forEach((button, slot) -> buttons.put(button, new GuiButtonSettings(slot, icons.get(button))));
        return new GuiSettings(rows, buttons, filler);
    }

    private GuiButton buttonAt(int slot) {
        for (Map.Entry<GuiButton, Integer> entry : slots.entrySet()) {
            if (entry.getValue() == slot) {
                return entry.getKey();
            }
        }
        return null;
    }
}
