package dev.novadev.novabank.bukkit.gui;

import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.bukkit.message.Language;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Menu principal d'administration ({@code /bank admin}) : un coffre de quatre lignes dont chaque bouton ouvre un editeur ou
 * lance une action (case 29 : comparateur, menu des parametres). Sans dependance serveur : l'etat et les actions viennent du {@link HubModel}.
 */
public final class AdminHub implements EditorScreen {

    /** Cases des boutons. */
    static final int ITEMS = 10;
    static final int GUI = 12;
    static final int ROWS = 14;
    static final int RESET = 16;
    static final int SAVE = 27;
    static final int SETTINGS = 29;
    static final int CANCEL = 31;
    static final int PUSH = 33;
    static final int RELOAD = 34;

    private static final ItemKey FILLER = ItemKey.minecraft("gray_stained_glass_pane");

    private final Supplier<Language> language;
    private final HubModel model;

    /**
     * @param language langue en vigueur
     * @param model    etat et actions
     */
    public AdminHub(Supplier<Language> language, HubModel model) {
        this.language = Objects.requireNonNull(language, "language");
        this.model = Objects.requireNonNull(model, "model");
    }

    @Override
    public String title() {
        return language.get().text("editor.hub.title");
    }

    @Override
    public int size() {
        return 36;
    }

    @Override
    public Optional<SlotView> slot(int slot) {
        Language text = language.get();
        boolean dirty = model.itemsDirty() || model.guiDirty() || model.settingsDirty();
        return Optional.of(switch (slot) {
            case ITEMS -> view("emerald", text, "items", "count", model.itemsCount(), "state", state(text, model.itemsDirty()));
            case GUI -> view("chest", text, "gui", "rows", model.guiRows(), "state", state(text, model.guiDirty()));
            case ROWS -> view("ladder", text, "rows", "rows", model.guiRows());
            case RESET -> view("sponge", text, "reset");
            case SETTINGS -> view("comparator", text, "settings", "state", state(text, model.settingsDirty()));
            case SAVE -> view("lime_dye", text, dirty ? "save" : "save-nothing");
            case CANCEL -> view("barrier", text, dirty ? "cancel" : "cancel-nothing");
            case PUSH -> view("ender_pearl", text, "push");
            case RELOAD -> view("clock", text, "reload");
            default -> new SlotView(FILLER, " ", List.of());
        });
    }

    @Override
    public void click(int slot, MenuClick click, Optional<ItemKey> inHand) {
        if (click != MenuClick.LEFT && click != MenuClick.RIGHT) {
            return;
        }
        switch (slot) {
            case ITEMS -> model.openItems();
            case GUI -> model.openGui();
            case ROWS -> model.changeRows(click == MenuClick.LEFT ? 1 : -1);
            case RESET -> model.resetGui();
            case SETTINGS -> model.openSettings();
            case SAVE -> confirmed(model.itemsDirty() || model.guiDirty() || model.settingsDirty(), model::save);
            case CANCEL -> confirmed(model.itemsDirty() || model.guiDirty() || model.settingsDirty(), model::cancel);
            case PUSH -> confirmed(model.canPush(), model::push);
            case RELOAD -> model.confirm(model::reload);
            default -> { }
        }
    }

    /** Confirmation seulement s'il y a quelque chose a faire ; sinon l'action annonce directement dans le chat qu'il n'y a rien. */
    private void confirmed(boolean somethingToDo, Runnable action) {
        if (somethingToDo) {
            model.confirm(action);
        } else {
            action.run();
        }
    }

    private static String state(Language text, boolean dirty) {
        return text.text(dirty ? "editor.hub.state-dirty" : "editor.hub.state-clean");
    }

    private static SlotView view(String icon, Language text, String key, Object... placeholders) {
        return new SlotView(ItemKey.minecraft(icon), text.text("editor.hub." + key + ".name", placeholders),
                SlotView.lines(text.text("editor.hub." + key + ".lore", placeholders)));
    }
}
