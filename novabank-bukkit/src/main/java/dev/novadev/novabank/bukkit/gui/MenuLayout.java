package dev.novadev.novabank.bukkit.gui;

import dev.novadev.novabank.config.model.GuiButton;
import dev.novadev.novabank.config.model.GuiSettings;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Disposition d'une GUI : les boutons occupent la case que la configuration leur donne ; le stock remplit, dans l'ordre,
 * les cases libres des lignes autres que la derniere (la barre) ; le remplissage couvre les cases libres de la barre.
 * Sans dependance serveur.
 */
public final class MenuLayout {

    private final int rows;
    private final Map<GuiButton, Integer> slots = new EnumMap<>(GuiButton.class);
    private final Map<Integer, GuiButton> buttons = new HashMap<>();
    private final List<Integer> stockSlots = new ArrayList<>();

    /**
     * @param settings section {@code gui} validee
     */
    public MenuLayout(GuiSettings settings) {
        Objects.requireNonNull(settings, "settings");
        this.rows = settings.rows();
        settings.buttons().forEach((button, position) -> {
            slots.put(button, position.slot());
            buttons.put(position.slot(), button);
        });
        for (int slot = 0; slot < barStart(); slot++) {
            if (!buttons.containsKey(slot)) {
                stockSlots.add(slot);
            }
        }
    }

    private int barStart() {
        return (rows - 1) * GuiSettings.COLUMNS;
    }

    /**
     * @return nombre de cases du coffre
     */
    public int size() {
        return rows * GuiSettings.COLUMNS;
    }

    /**
     * @return nombre d'items du stock par page
     */
    public int pageSize() {
        return stockSlots.size();
    }

    /**
     * @param index index de l'entree dans la page
     * @return la case du coffre qui l'affiche
     */
    public int stockSlot(int index) {
        return stockSlots.get(index);
    }

    /**
     * @param slot case brute du coffre
     * @return l'index dans la page si la case affiche le stock
     */
    public Optional<Integer> stockIndex(int slot) {
        int index = stockSlots.indexOf(slot);
        return index >= 0 ? Optional.of(index) : Optional.empty();
    }

    /**
     * @param slot case brute du coffre
     * @return le bouton de cette case
     */
    public Optional<GuiButton> button(int slot) {
        return Optional.ofNullable(buttons.get(slot));
    }

    /**
     * @param button bouton
     * @return sa case
     */
    public int slot(GuiButton button) {
        return slots.get(button);
    }

    /**
     * @return cases de la derniere ligne sans bouton (remplissage)
     */
    public List<Integer> fillerSlots() {
        List<Integer> free = new ArrayList<>();
        for (int slot = barStart(); slot < size(); slot++) {
            if (!buttons.containsKey(slot)) {
                free.add(slot);
            }
        }
        return free;
    }
}
