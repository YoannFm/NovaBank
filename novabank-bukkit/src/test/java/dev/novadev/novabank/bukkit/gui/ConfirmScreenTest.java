package dev.novadev.novabank.bukkit.gui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.bukkit.testing.TestLanguages;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ConfirmScreenTest {

    private final List<String> calls = new ArrayList<>();
    private final ConfirmScreen screen = new ConfirmScreen(TestLanguages::french, () -> calls.add("ok"),
            () -> calls.add("non"));

    @Test
    void vitres_vertes_annulent_vitres_rouges_confirment_le_reste_est_vide() {
        screen.click(0, MenuClick.LEFT, Optional.empty());
        screen.click(20, MenuClick.LEFT, Optional.empty());
        screen.click(6, MenuClick.LEFT, Optional.empty());
        screen.click(26, MenuClick.LEFT, Optional.empty());
        screen.click(13, MenuClick.LEFT, Optional.empty());
        screen.click(6, MenuClick.SHIFT, Optional.empty());

        assertThat(calls).containsExactly("non", "non", "ok", "ok");
        assertThat(screen.slot(13)).isEmpty();
        assertThat(screen.slot(0)).hasValueSatisfying(view -> assertThat(view.name()).contains("ANNULER"));
        assertThat(screen.slot(8)).hasValueSatisfying(view -> assertThat(view.name()).contains("CONFIRMER"));
    }
}
