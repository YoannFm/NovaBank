package dev.novadev.novabank.bukkit.gui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.bukkit.config.Setting;
import dev.novadev.novabank.bukkit.config.SettingsCatalog;
import dev.novadev.novabank.bukkit.config.SettingsCatalog.Category;
import dev.novadev.novabank.bukkit.config.SettingsDraft;
import dev.novadev.novabank.bukkit.testing.TestLanguages;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SettingsScreensTest {

    private final SettingsDraft draft = new SettingsDraft(Map.of(
            "limits", Map.of("cooldown-ms", 500, "max-quantity-per-operation", 2304),
            "security", Map.of("save-player-data", true, "shutdown-drain-timeout-ms", 10000),
            "banks", Map.of("entity-types", List.of("player"), "default-priority", List.of("player")),
            "storage", Map.of("sql", Map.of("password", "secret"))));
    private final List<String> calls = new ArrayList<>();
    private boolean secrets;

    private final SettingsScreens.Navigation navigation = new SettingsScreens.Navigation() {
        @Override
        public void hub() {
            calls.add("hub");
        }

        @Override
        public void home() {
            calls.add("home");
        }

        @Override
        public void category(Category category) {
            calls.add("category " + category.id());
        }

        @Override
        public void special(Setting setting) {
            calls.add("special " + setting.path());
        }

        @Override
        public void ask(Setting setting) {
            calls.add("ask " + setting.path());
        }

        @Override
        public boolean secretsAllowed() {
            return secrets;
        }

        @Override
        public void secretDenied() {
            calls.add("denied");
        }
    };

    private Category category(String id) {
        return SettingsCatalog.categories(draft.types()).stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
    }

    private static String lore(SlotView view) {
        return String.join("\n", view.lore());
    }

    @Test
    void accueil_une_icone_par_categorie_et_retour() {
        SettingsScreens.Home home = new SettingsScreens.Home(TestLanguages::french, draft,
                SettingsCatalog.categories(draft.types()), navigation);

        assertThat(home.slot(10)).hasValueSatisfying(view -> assertThat(view.name()).contains("Langue et serveur"));
        assertThat(home.slot(23)).hasValueSatisfying(view -> assertThat(view.name()).contains("Redis"));
        home.click(11, MenuClick.LEFT, Optional.empty());
        home.click(SettingsScreens.Home.HOME_BACK, MenuClick.LEFT, Optional.empty());
        assertThat(calls).containsExactly("category banks", "hub");
    }

    @Test
    void page_valeurs_clics_et_marque_modifie() {
        SettingsScreens.Page page = new SettingsScreens.Page(TestLanguages::french, draft, category("limits"), navigation);

        assertThat(page.slot(1)).hasValueSatisfying(view -> {
            assertThat(view.name()).contains("Delai entre operations");
            assertThat(lore(view)).contains("500").doesNotContain("Modifie");
            // Aide sur plusieurs lignes (une par geste), pas repliee en une seule.
            assertThat(view.lore()).anySatisfy(line -> assertThat(line).startsWith("§eClic droit"));
        });
        page.click(1, MenuClick.LEFT, Optional.empty());
        page.click(1, MenuClick.LEFT, Optional.empty());
        assertThat(draft.number("limits.cooldown-ms")).isEqualByComparingTo("700");
        assertThat(page.slot(1)).hasValueSatisfying(view -> assertThat(lore(view)).contains("700").contains("Modifie"));

        page.click(2, MenuClick.LEFT, Optional.empty());
        assertThat(draft.bool("security.save-player-data")).isFalse();
        page.click(1, MenuClick.SHIFT, Optional.empty());
        page.click(SettingsScreens.BACK, MenuClick.LEFT, Optional.empty());
        assertThat(calls).containsExactly("ask limits.cooldown-ms", "home");
    }

    @Test
    void secret_masque_et_refuse_sans_permission() {
        SettingsScreens.Page page = new SettingsScreens.Page(TestLanguages::french, draft, category("storage"), navigation);
        int password = category("storage").settings().stream().map(Setting::path).toList().indexOf("storage.sql.password");

        assertThat(page.slot(password)).hasValueSatisfying(view -> assertThat(lore(view)).doesNotContain("secret")
                .contains("********").contains("redemarrage"));
        page.click(password, MenuClick.LEFT, Optional.empty());
        secrets = true;
        page.click(password, MenuClick.LEFT, Optional.empty());
        assertThat(calls).containsExactly("denied", "ask storage.sql.password");
    }

    @Test
    void types_et_ordre_de_choix() {
        SettingsScreens.Types types = new SettingsScreens.Types(TestLanguages::french, draft,
                () -> List.of("player", "island"), false, () -> calls.add("back"));
        types.click(1, MenuClick.LEFT, Optional.empty());
        assertThat(draft.types()).containsExactly("player", "island");
        assertThat(types.slot(1)).hasValueSatisfying(view -> {
            assertThat(view.icon()).isEqualTo(ItemKey.minecraft("grass_block"));
            assertThat(lore(view)).contains("Actif");
        });

        SettingsScreens.Types priority = new SettingsScreens.Types(TestLanguages::french, draft, draft::types, true,
                () -> calls.add("back"));
        priority.click(1, MenuClick.LEFT, Optional.empty());
        assertThat(draft.priority()).containsExactly("player", "island");
        assertThat(priority.slot(1)).hasValueSatisfying(view -> assertThat(lore(view)).contains("Choix n.2"));
        priority.click(SettingsScreens.BACK, MenuClick.LEFT, Optional.empty());
        assertThat(calls).containsExactly("back");
    }

    @Test
    void roles_une_ligne_par_type() {
        draft.toggleType("island");
        SettingsScreens.Roles roles = new SettingsScreens.Roles(TestLanguages::french, draft, () -> { });

        assertThat(roles.slot(9)).hasValueSatisfying(view -> assertThat(view.name()).contains("island"));
        assertThat(roles.slot(9 + 7)).hasValueSatisfying(view -> {
            assertThat(view.icon()).isEqualTo(ItemKey.minecraft("gray_dye"));
            assertThat(view.name()).contains("Retrait").contains("member");
        });
        roles.click(9 + 7, MenuClick.LEFT, Optional.empty());
        assertThat(draft.roles("island", true)).containsExactly("owner", "admin", "member");
        assertThat(draft.roles("player", true)).containsExactly("owner", "admin");
    }

    @Test
    void saisies_au_clavier() {
        Setting port = category("storage").settings().stream().filter(s -> s.path().equals("storage.sql.port"))
                .findFirst().orElseThrow();
        Setting options = category("storage").settings().stream().filter(s -> s.path().equals("storage.sql.options"))
                .findFirst().orElseThrow();
        Setting aliases = category("commands").settings().get(1);

        assertThat(SettingsScreens.parse(port, "3307")).contains(3307L);
        assertThat(SettingsScreens.parse(port, "70000")).isEmpty();
        assertThat(SettingsScreens.parse(port, "abc")).isEmpty();
        assertThat(SettingsScreens.parse(options, "sslMode=verify-full; serverSslCert=/ca.pem"))
                .contains(Map.of("serverSslCert", "/ca.pem", "sslMode", "verify-full"));
        assertThat(SettingsScreens.parse(options, "bad key=1")).isEmpty();
        assertThat(SettingsScreens.parse(aliases, "banque, coffre")).contains(List.of("banque", "coffre"));
        assertThat(SettingsScreens.parse(aliases, "-")).contains(List.of());
        Setting multiplier = category("integrations").settings().get(2);
        assertThat(SettingsScreens.parse(multiplier, "1,5")).contains(new BigDecimal("1.5"));
    }
}
