package dev.novadev.novabank.config.model;

import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.core.text.ConsoleText;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Section {@code gui} : disposition de la GUI de banque. Les textes (titre, noms et
 * descriptions) sont dans les fichiers de langue.
 *
 * @param rows    nombre de lignes du coffre ; la derniere est la barre (remplissage autour des boutons)
 * @param buttons case et icone de chaque bouton (tous presents, cases distinctes, dans le coffre)
 * @param filler  item des cases vides de la barre, ou vide
 */
public record GuiSettings(int rows, Map<GuiButton, GuiButtonSettings> buttons, Optional<ItemKey> filler) {

    /** Cases par ligne d'un coffre. */
    public static final int COLUMNS = 9;
    /** Lignes au minimum : une de stock, une de boutons. */
    public static final int MIN_ROWS = 2;
    /** Lignes au maximum d'un coffre. */
    public static final int MAX_ROWS = 6;

    /**
     * @throws IllegalArgumentException si le nombre de lignes est hors bornes, un bouton manque ou sort du coffre, ou deux
     *                                  boutons se partagent une case
     */
    public GuiSettings {
        if (rows < MIN_ROWS || rows > MAX_ROWS) {
            throw new IllegalArgumentException(ConsoleText.tr("Lignes hors bornes : {0}", rows));
        }
        Map<GuiButton, GuiButtonSettings> copy = new EnumMap<>(GuiButton.class);
        copy.putAll(buttons);
        if (copy.size() != GuiButton.values().length) {
            throw new IllegalArgumentException(ConsoleText.tr("Boutons manquants : {0}", copy.keySet()));
        }
        java.util.Set<Integer> used = new java.util.HashSet<>();
        for (Map.Entry<GuiButton, GuiButtonSettings> entry : copy.entrySet()) {
            int slot = entry.getValue().slot();
            if (slot >= rows * COLUMNS) {
                throw new IllegalArgumentException(ConsoleText.tr("Bouton '{0}' hors du coffre : case {1}",
                        entry.getKey().key(), slot));
            }
            if (!used.add(slot)) {
                throw new IllegalArgumentException(ConsoleText.tr("Case {0} occupee par deux boutons", slot));
            }
        }
        buttons = Collections.unmodifiableMap(copy);
        Objects.requireNonNull(filler, "filler");
    }
}
