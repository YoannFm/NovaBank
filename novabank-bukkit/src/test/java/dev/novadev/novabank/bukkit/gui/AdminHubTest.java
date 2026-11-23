package dev.novadev.novabank.bukkit.gui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.bukkit.testing.TestLanguages;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AdminHubTest {

    private static final class FakeModel implements HubModel {

        final List<String> calls = new ArrayList<>();
        boolean itemsDirty;
        boolean guiDirty;
        boolean settingsDirty;
        boolean canPush = true;
        int rows = 6;

        @Override
        public int itemsCount() {
            return 7;
        }

        @Override
        public boolean itemsDirty() {
            return itemsDirty;
        }

        @Override
        public int guiRows() {
            return rows;
        }

        @Override
        public boolean guiDirty() {
            return guiDirty;
        }

        @Override
        public boolean settingsDirty() {
            return settingsDirty;
        }

        @Override
        public void openSettings() {
            calls.add("settings");
        }

        @Override
        public void openItems() {
            calls.add("items");
        }

        @Override
        public void openGui() {
            calls.add("gui");
        }

        @Override
        public void changeRows(int delta) {
            calls.add("rows " + delta);
        }

        @Override
        public void resetGui() {
            calls.add("reset");
        }

        @Override
        public boolean canPush() {
            return canPush;
        }

        @Override
        public void confirm(Runnable action) {
            calls.add("confirm");
            action.run();
        }

        @Override
        public void save() {
            calls.add("save");
        }

        @Override
        public void cancel() {
            calls.add("cancel");
        }

        @Override
        public void push() {
            calls.add("push");
        }

        @Override
        public void reload() {
            calls.add("reload");
        }
    }

    private final FakeModel model = new FakeModel();
    private final AdminHub hub = new AdminHub(TestLanguages::french, model);

    @Test
    void chaque_bouton_declenche_son_action_et_les_autres_cases_sont_inertes() {
        model.itemsDirty = true;
        hub.click(AdminHub.ITEMS, MenuClick.LEFT, Optional.empty());
        hub.click(AdminHub.GUI, MenuClick.LEFT, Optional.empty());
        hub.click(AdminHub.ROWS, MenuClick.LEFT, Optional.empty());
        hub.click(AdminHub.ROWS, MenuClick.RIGHT, Optional.empty());
        hub.click(AdminHub.RESET, MenuClick.LEFT, Optional.empty());
        hub.click(AdminHub.SETTINGS, MenuClick.LEFT, Optional.empty());
        hub.click(AdminHub.SAVE, MenuClick.LEFT, Optional.empty());
        hub.click(AdminHub.CANCEL, MenuClick.LEFT, Optional.empty());
        hub.click(AdminHub.PUSH, MenuClick.LEFT, Optional.empty());
        hub.click(AdminHub.RELOAD, MenuClick.LEFT, Optional.empty());
        hub.click(0, MenuClick.LEFT, Optional.empty());
        hub.click(AdminHub.SAVE, MenuClick.SHIFT, Optional.empty()); // seuls clic gauche et droit comptent
        hub.click(AdminHub.SAVE, MenuClick.OTHER, Optional.empty());

        assertThat(model.calls).containsExactly("items", "gui", "rows 1", "rows -1", "reset", "settings", "confirm", "save", "confirm",
                "cancel", "confirm", "push", "confirm", "reload");
    }

    @Test
    void l_affichage_suit_l_etat_des_brouillons() {
        assertThat(hub.size()).isEqualTo(36);
        assertThat(hub.slot(AdminHub.ITEMS)).hasValueSatisfying(view -> {
            assertThat(view.icon()).isEqualTo(ItemKey.minecraft("emerald"));
            assertThat(String.join(" ", view.lore())).contains("7 items").contains("A jour");
        });
        assertThat(hub.slot(AdminHub.SAVE)).hasValueSatisfying(view -> assertThat(view.name()).contains("Enregistrer")
                .doesNotContain("deployer"));
        assertThat(hub.slot(AdminHub.CANCEL)).hasValueSatisfying(view -> assertThat(view.name()).contains("Abandonner")
                .doesNotContain("changements"));

        model.itemsDirty = true;
        model.rows = 4;

        assertThat(hub.slot(AdminHub.ITEMS)).hasValueSatisfying(view -> assertThat(String.join(" ", view.lore()))
                .contains("pas encore enregistre"));
        assertThat(hub.slot(AdminHub.GUI)).hasValueSatisfying(view -> assertThat(String.join(" ", view.lore()))
                .contains("4 lignes").contains("A jour"));
        assertThat(hub.slot(AdminHub.ROWS)).hasValueSatisfying(view -> assertThat(view.name()).contains("4"));
        assertThat(hub.slot(AdminHub.SAVE)).hasValueSatisfying(view -> assertThat(view.name()).contains("Enregistrer et deployer"));
        assertThat(hub.slot(AdminHub.CANCEL)).hasValueSatisfying(view -> assertThat(view.name()).contains("Abandonner les changements"));
        assertThat(hub.slot(0)).hasValueSatisfying(view -> assertThat(view.icon())
                .isEqualTo(ItemKey.minecraft("gray_stained_glass_pane")));
    }

    @Test
    void le_comparateur_ouvre_les_parametres_et_compte_dans_enregistrer() {
        assertThat(hub.slot(AdminHub.SETTINGS)).hasValueSatisfying(view -> {
            assertThat(view.icon()).isEqualTo(ItemKey.minecraft("comparator"));
            assertThat(view.name()).contains("Parametres");
            assertThat(String.join(" ", view.lore())).contains("A jour");
        });

        model.settingsDirty = true;

        assertThat(hub.slot(AdminHub.SETTINGS)).hasValueSatisfying(view -> assertThat(String.join(" ", view.lore()))
                .contains("pas encore enregistre"));
        assertThat(hub.slot(AdminHub.SAVE)).hasValueSatisfying(view -> assertThat(view.name()).contains("Enregistrer et deployer"));
        hub.click(AdminHub.SAVE, MenuClick.LEFT, Optional.empty());
        assertThat(model.calls).containsExactly("confirm", "save");
    }

    @Test
    void sans_brouillon_ni_renvoi_possible_le_message_part_direct_sans_confirmation() {
        model.canPush = false;

        hub.click(AdminHub.SAVE, MenuClick.LEFT, Optional.empty());
        hub.click(AdminHub.CANCEL, MenuClick.LEFT, Optional.empty());
        hub.click(AdminHub.PUSH, MenuClick.LEFT, Optional.empty());
        hub.click(AdminHub.RELOAD, MenuClick.LEFT, Optional.empty());

        assertThat(model.calls).containsExactly("save", "cancel", "push", "confirm", "reload");
    }
}
