package dev.novadev.novabank.bukkit.gui;

import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.bukkit.message.Language;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** Ecran de confirmation : vitres rouges ANNULER a gauche, vitres vertes CONFIRMER a droite, le reste vide. */
public final class ConfirmScreen implements EditorScreen {

    static final List<Integer> CANCEL = List.of(0, 1, 2, 9, 10, 11, 18, 19, 20);
    static final List<Integer> CONFIRM = List.of(6, 7, 8, 15, 16, 17, 24, 25, 26);

    private final Supplier<Language> language;
    private final Runnable onConfirm;
    private final Runnable onCancel;

    /**
     * @param language  langue en vigueur
     * @param onConfirm action executee par CONFIRMER
     * @param onCancel  retour a l'ecran precedent, sans rien executer
     */
    public ConfirmScreen(Supplier<Language> language, Runnable onConfirm, Runnable onCancel) {
        this.language = Objects.requireNonNull(language, "language");
        this.onConfirm = Objects.requireNonNull(onConfirm, "onConfirm");
        this.onCancel = Objects.requireNonNull(onCancel, "onCancel");
    }

    @Override
    public String title() {
        return language.get().text("editor.confirm.title");
    }

    @Override
    public int size() {
        return 27;
    }

    @Override
    public Optional<SlotView> slot(int slot) {
        if (CANCEL.contains(slot)) {
            return Optional.of(new SlotView(ItemKey.minecraft("red_stained_glass_pane"),
                    language.get().text("editor.confirm.cancel"), List.of()));
        }
        if (CONFIRM.contains(slot)) {
            return Optional.of(new SlotView(ItemKey.minecraft("green_stained_glass_pane"),
                    language.get().text("editor.confirm.confirm"), List.of()));
        }
        return Optional.empty();
    }

    @Override
    public void click(int slot, MenuClick click, Optional<ItemKey> inHand) {
        if (click != MenuClick.LEFT && click != MenuClick.RIGHT) {
            return;
        }
        if (CANCEL.contains(slot)) {
            onCancel.run();
        } else if (CONFIRM.contains(slot)) {
            onConfirm.run();
        }
    }
}
