package dev.novadev.novabank.bukkit.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.bukkit.event.inventory.ClickType;
import org.junit.jupiter.api.Test;

class ClickKindsTest {

    @Test
    void seuls_gauche_droit_et_majuscule_declenchent_une_action() {
        assertThat(ClickKinds.of(ClickType.LEFT)).isEqualTo(MenuClick.LEFT);
        assertThat(ClickKinds.of(ClickType.RIGHT)).isEqualTo(MenuClick.RIGHT);
        assertThat(ClickKinds.of(ClickType.SHIFT_LEFT)).isEqualTo(MenuClick.SHIFT);
        assertThat(ClickKinds.of(ClickType.SHIFT_RIGHT)).isEqualTo(MenuClick.SHIFT);
        assertThat(Arrays.stream(ClickType.values())
                .filter(type -> type != ClickType.LEFT && type != ClickType.RIGHT && type != ClickType.SHIFT_LEFT
                        && type != ClickType.SHIFT_RIGHT)
                .map(ClickKinds::of))
                .isNotEmpty()
                .containsOnly(MenuClick.OTHER);
    }
}
