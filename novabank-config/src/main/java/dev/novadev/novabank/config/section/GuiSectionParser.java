package dev.novadev.novabank.config.section;

import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.config.catalog.ItemAvailability;
import dev.novadev.novabank.config.model.GuiButton;
import dev.novadev.novabank.config.model.GuiButtonSettings;
import dev.novadev.novabank.config.model.GuiSettings;
import dev.novadev.novabank.config.tree.ConfigNode;
import dev.novadev.novabank.core.text.ConsoleText;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Section {@code gui} : nombre de lignes, case (0 a 53, ou colonne de la derniere ligne pour les anciennes configurations) et icone
 * de chaque bouton, item de remplissage. Une icone inconnue de cette version est
 * remplacee par l'icone par defaut (avertissement) ; deux boutons sur la meme case
 * sont une erreur. Un remplissage vide ({@code filler: ""}) laisse les cases vides.
 */
public final class GuiSectionParser {

    private static final Map<GuiButton, GuiButtonSettings> DEFAULTS = defaults();
    private static final ItemKey DEFAULT_FILLER = ItemKey.minecraft("gray_stained_glass_pane");

    private GuiSectionParser() {
    }

    /**
     * Lit et valide la section.
     *
     * @param root         racine de la configuration
     * @param availability items disponibles sur ce serveur
     * @return la section
     */
    public static GuiSettings parse(ConfigNode root, ItemAvailability availability) {
        ConfigNode node = root.section("gui");
        int rows = node.intValue("rows", GuiSettings.MAX_ROWS, GuiSettings.MIN_ROWS, GuiSettings.MAX_ROWS);
        ConfigNode buttonsNode = node.section("buttons");
        Map<GuiButton, GuiButtonSettings> buttons = new EnumMap<>(GuiButton.class);
        Map<Integer, GuiButton> slots = new HashMap<>();
        boolean invalid = false;
        int barStart = (rows - 1) * GuiSettings.COLUMNS;
        for (GuiButton button : GuiButton.values()) {
            GuiButtonSettings fallback = DEFAULTS.get(button);
            ConfigNode buttonNode = buttonsNode.section(button.key());
            // "slot" : case absolue du coffre ; "column" (anciennes configurations) : colonne de la derniere ligne.
            int slot = buttonNode.has("slot")
                    ? buttonNode.intValue("slot", barStart + fallback.slot(), 0, rows * GuiSettings.COLUMNS - 1)
                    : barStart + buttonNode.intValue("column", fallback.slot(), 0, GuiSettings.COLUMNS - 1);
            ItemKey icon = icon(buttonNode, "icon", availability).orElse(fallback.icon());
            GuiButton previous = slots.putIfAbsent(slot, button);
            if (previous != null) {
                invalid = true;
                buttonNode.error(buttonNode.has("slot") ? "slot" : "column",
                        ConsoleText.tr("case {0} deja occupee par le bouton '{1}'", slot, previous.key()));
            }
            buttons.put(button, new GuiButtonSettings(slot, icon));
        }
        Optional<ItemKey> filler = node.has("filler") && node.optionalText("filler").isEmpty()
                ? Optional.empty()
                : Optional.of(icon(node, "filler", availability).orElse(DEFAULT_FILLER));
        if (invalid) { // erreur deja signalee : disposition par defaut pour que l'objet reste valide
            DEFAULTS.forEach((button, defaults) -> buttons.put(button,
                    new GuiButtonSettings(barStart + defaults.slot(), defaults.icon())));
        }
        return new GuiSettings(rows, buttons, filler);
    }

    private static Optional<ItemKey> icon(ConfigNode node, String key, ItemAvailability availability) {
        Optional<String> text = node.optionalText(key);
        if (text.isEmpty()) {
            return Optional.empty();
        }
        ItemKey icon;
        try {
            icon = ItemKey.parse(text.get());
        } catch (IllegalArgumentException invalid) {
            node.error(key,
                    ConsoleText.tr("cle d'item invalide '{0}' (attendu namespace:id en minuscules, par exemple minecraft:stone)",
                            text.get()));
            return Optional.empty();
        }
        if (!ItemKey.MINECRAFT.equals(icon.namespace())) {
            node.error(key, ConsoleText.tr("icone vanilla attendue (minecraft:...), recu : '{0}'", icon));
            return Optional.empty();
        }
        if (!availability.exists(icon)) {
            node.warning(key,
                    ConsoleText.tr("item inconnu dans cette version du serveur : '{0}', valeur par defaut utilisee",
                            icon));
            return Optional.empty();
        }
        return Optional.of(icon);
    }

    /**
     * @return position par defaut de chaque bouton : colonne de la barre (0 a 8) dans {@code slot}, et icone par defaut
     */
    public static Map<GuiButton, GuiButtonSettings> defaultButtons() {
        return DEFAULTS;
    }

    private static Map<GuiButton, GuiButtonSettings> defaults() {
        Map<GuiButton, GuiButtonSettings> defaults = new EnumMap<>(GuiButton.class);
        defaults.put(GuiButton.INFO, new GuiButtonSettings(0, ItemKey.minecraft("book")));
        defaults.put(GuiButton.PREVIOUS, new GuiButtonSettings(3, ItemKey.minecraft("arrow")));
        defaults.put(GuiButton.DEPOSIT_ALL, new GuiButtonSettings(4, ItemKey.minecraft("hopper")));
        defaults.put(GuiButton.NEXT, new GuiButtonSettings(5, ItemKey.minecraft("arrow")));
        defaults.put(GuiButton.CLOSE, new GuiButtonSettings(8, ItemKey.minecraft("barrier")));
        return defaults;
    }
}
