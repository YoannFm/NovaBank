package dev.novadev.novabank.bukkit.command;

import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.bukkit.testing.TestLanguages;
import dev.novadev.novabank.core.access.Permissions;
import dev.novadev.novabank.core.application.Amount;
import dev.novadev.novabank.core.application.ItemChoice;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlayerCommandRouterTest {

    private final List<String> calls = new ArrayList<>();
    private final PlayerCommandRouter router = new PlayerCommandRouter(new PlayerActions() {
        @Override
        public void deposit(CommandActor actor, UUID playerId, ItemChoice choice, Amount amount) {
            calls.add("deposit " + choice.kind() + " " + choice.item().map(ItemKey::toString).orElse("-") + " "
                    + (amount.all() ? "all" : amount.value()));
        }

        @Override
        public void withdraw(CommandActor actor, UUID playerId, ItemKey item, Amount amount) {
            calls.add("withdraw " + item + " " + (amount.all() ? "all" : amount.value()));
        }

        @Override
        public void info(CommandActor actor, UUID playerId, String label) {
            calls.add("info");
        }

        @Override
        public void select(CommandActor actor, UUID playerId, String entityType) {
            calls.add("select " + entityType);
        }

        @Override
        public void top(CommandActor actor, UUID playerId, String entityType) {
            calls.add("top " + entityType);
        }

        @Override
        public void open(CommandActor actor, UUID playerId) {
            calls.add("open");
        }
    }, new FakeCompletions(), TestLanguages::french);

    private final RecordingActor player = new RecordingActor(false);

    @Test
    void lecture_des_arguments_de_depot_et_de_retrait() {
        run("deposit", "stone", "64");
        run("deposit", "Minecraft:Diamond_Block");
        run("deposit", "hand", "3");
        run("deposit", "HAND");
        run("deposit", "all");
        run("withdraw", "itemsadder:pack/ruby", "all");
        run("withdraw", "stone", "10");

        assertThat(calls).containsExactly("deposit ITEM minecraft:stone 64", "deposit ITEM minecraft:diamond_block all",
                "deposit HELD - 3", "deposit HELD - all", "deposit ALL_ACCEPTED - all",
                "withdraw itemsadder:pack/ruby all", "withdraw minecraft:stone 10");
        assertThat(player.received()).isEmpty();
    }

    @Test
    void sans_argument_gui_puis_info_et_choix_de_banque() {
        run();
        run("OPEN");
        run("info");
        run("select", "Island");

        assertThat(calls).containsExactly("open", "open", "info", "select island");
    }

    @Test
    void classement_avec_ou_sans_type_et_trop_d_arguments() {
        run("top");
        run("TOP", "Island");
        run("top", "player", "blocs", "extra");

        assertThat(calls).containsExactly("top null", "top island");
        assertThat(player.received()).first().satisfies(line -> assertThat(line).contains("commandes"));
    }

    @Test
    void arguments_invalides_signales_sans_action() {
        run("deposit", "stone", "0");
        run("deposit", "pierre bleue");
        run("withdraw", "stone", "-3");
        run("withdraw", "stone");
        run("deposit", "all", "5");
        run("vider");

        assertThat(calls).isEmpty();
        assertThat(player.received()).startsWith("NovaBank » Quantite invalide : 0 (nombre entier positif ou 'all').",
                "NovaBank » Item invalide : pierre bleue.",
                "NovaBank » Quantite invalide : -3 (nombre entier positif ou 'all').",
                "NovaBank - commandes :");
        assertThat(player.received()).last().satisfies(line -> assertThat(line).contains("Sous-commande inconnue"));
    }

    @Test
    void console_et_joueur_sans_permission_refuses() {
        RecordingActor console = new RecordingActor(true, true);
        router.execute(console, "bank", new String[] {"info"});
        RecordingActor denied = new RecordingActor(false).deny(Permissions.USE);
        router.execute(denied, "bank", new String[] {"info"});

        assertThat(calls).isEmpty();
        assertThat(console.received()).containsExactly("NovaBank » Cette commande est reservee aux joueurs.");
        assertThat(denied.received()).singleElement().satisfies(line -> assertThat(line).contains("permission"));
        assertThat(router.complete(denied, new String[] {""})).isEmpty();
    }

    @Test
    void completion_des_sous_commandes_items_et_types() {
        assertThat(router.complete(player, new String[] {""}))
                .containsExactly("deposit", "help", "info", "open", "select", "top", "withdraw");
        assertThat(router.complete(player, new String[] {"deposit", ""}))
                .containsExactly("all", "diamond_block", "hand", "itemsadder:pack/ruby", "stone");
        assertThat(router.complete(player, new String[] {"withdraw", "d"})).containsExactly("diamond_block", "dirt");
        assertThat(router.complete(player, new String[] {"withdraw", "stone", ""})).containsExactly("all");
        assertThat(router.complete(player, new String[] {"deposit", "all", ""})).isEmpty();
        assertThat(router.complete(player, new String[] {"select", "i"})).containsExactly("island");
        assertThat(router.complete(player, new String[] {"info", ""})).isEmpty();
    }

    private void run(String... args) {
        router.execute(player, "bank", args);
    }

    @Test
    void bank_admin_ouvre_le_menu_principal_seulement_pour_un_administrateur() {
        List<String> opened = new ArrayList<>();
        AdminSubcommand hub = new AdminSubcommand() {
            @Override
            public String permission() {
                return Permissions.ADMIN;
            }

            @Override
            public void execute(CommandActor actor, String label, String[] args) {
                opened.add("hub");
            }

            @Override
            public List<String> complete(String[] args) {
                return List.of();
            }
        };
        PlayerCommandRouter withHub = new PlayerCommandRouter(new PlayerActions() {
            @Override
            public void deposit(CommandActor actor, UUID playerId, ItemChoice choice, Amount amount) {
            }

            @Override
            public void withdraw(CommandActor actor, UUID playerId, ItemKey item, Amount amount) {
            }

            @Override
            public void info(CommandActor actor, UUID playerId, String label) {
            }

            @Override
            public void select(CommandActor actor, UUID playerId, String entityType) {
            }

            @Override
            public void top(CommandActor actor, UUID playerId, String entityType) {
            }

            @Override
            public void open(CommandActor actor, UUID playerId) {
            }
        }, new FakeCompletions(), TestLanguages::french, hub);
        RecordingActor admin = new RecordingActor(true);
        RecordingActor player = new RecordingActor(false);

        withHub.execute(admin, "bank", new String[] {"admin"});
        withHub.execute(player, "bank", new String[] {"admin"});

        assertThat(opened).containsExactly("hub");
        assertThat(player.received()).hasSize(1);
        assertThat(withHub.complete(admin, new String[] {"ad"})).containsExactly("admin");
        assertThat(withHub.complete(player, new String[] {"ad"})).isEmpty();
        assertThat(router.complete(admin, new String[] {"ad"})).isEmpty(); // sans menu branche : pas propose
    }
}
