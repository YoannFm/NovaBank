package dev.novadev.novabank.bukkit.gui;

import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.config.model.GuiButton;
import dev.novadev.novabank.config.model.GuiButtonSettings;
import dev.novadev.novabank.config.model.GuiSettings;
import dev.novadev.novabank.core.application.BankSnapshot;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Donnees de test de la GUI.
 */
final class MenuFixtures {

    static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    static final EntityRef BANK = EntityRef.player(PLAYER);
    static final ItemKey STONE = ItemKey.minecraft("stone");

    private MenuFixtures() {
    }

    /** Disposition par defaut : boutons de la derniere ligne, colonnes info 0, precedent 3, deposer 4, suivant 5, fermer 8. */
    static GuiSettings settings(int rows) {
        int bar = (rows - 1) * GuiSettings.COLUMNS;
        Map<GuiButton, GuiButtonSettings> buttons = new EnumMap<>(GuiButton.class);
        buttons.put(GuiButton.INFO, new GuiButtonSettings(bar + 0, ItemKey.minecraft("book")));
        buttons.put(GuiButton.PREVIOUS, new GuiButtonSettings(bar + 3, ItemKey.minecraft("arrow")));
        buttons.put(GuiButton.DEPOSIT_ALL, new GuiButtonSettings(bar + 4, ItemKey.minecraft("hopper")));
        buttons.put(GuiButton.NEXT, new GuiButtonSettings(bar + 5, ItemKey.minecraft("arrow")));
        buttons.put(GuiButton.CLOSE, new GuiButtonSettings(bar + 8, ItemKey.minecraft("barrier")));
        return new GuiSettings(rows, buttons, Optional.empty());
    }

    /** Banque de {@code items} items differents, item i en quantite i+1 et valeur i+1. */
    static BankSnapshot snapshot(int items) {
        Map<ItemKey, Long> stock = new LinkedHashMap<>();
        Map<ItemKey, BigDecimal> values = new LinkedHashMap<>();
        for (int i = 0; i < items; i++) {
            ItemKey key = ItemKey.minecraft(String.format("item_%03d", i));
            stock.put(key, i + 1L);
            values.put(key, BigDecimal.valueOf(i + 1L));
        }
        return new BankSnapshot(BANK, true, BigDecimal.TEN, stock, values);
    }

    static BankSnapshot stoneBank(long quantity) {
        return new BankSnapshot(BANK, true, BigDecimal.valueOf(quantity), Map.of(STONE, quantity),
                Map.of(STONE, BigDecimal.valueOf(quantity)));
    }

    /** Inventaire : piles de 64, place donnee. */
    static InventoryRoom room(long room) {
        return new InventoryRoom() {
            @Override
            public int maxStackSize(ItemKey item) {
                return 64;
            }

            @Override
            public long room(ItemKey item) {
                return room;
            }
        };
    }
}
