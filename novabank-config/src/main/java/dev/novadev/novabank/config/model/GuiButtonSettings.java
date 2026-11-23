package dev.novadev.novabank.config.model;

import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.core.text.ConsoleText;
import java.util.Objects;

/**
 * Position et icone d'un bouton de la GUI.
 *
 * @param slot   case du coffre (0 a 53), comptee depuis le haut a gauche
 * @param icon   item vanilla affiche
 */
public record GuiButtonSettings(int slot, ItemKey icon) {

    /**
     * @throws IllegalArgumentException si la case est hors du plus grand coffre
     */
    public GuiButtonSettings {
        Objects.requireNonNull(icon, "icon");
        if (slot < 0 || slot >= GuiSettings.MAX_ROWS * GuiSettings.COLUMNS) {
            throw new IllegalArgumentException(ConsoleText.tr("Case hors du coffre : {0}", slot));
        }
    }
}
