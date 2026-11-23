package dev.novadev.novabank.bukkit.command;

import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.bukkit.testing.TestLanguages;
import dev.novadev.novabank.core.access.Permissions;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdminCommandRouterTest {

    private final List<String> calls = new ArrayList<>();
    private final AdminCommandRouter router = new AdminCommandRouter(new AdminActions() {
        @Override
        public void diagnostic(CommandActor actor) {
            calls.add("diagnostic");
        }

        @Override
        public void reload(CommandActor actor) {
            calls.add("reload");
        }
    }, new BankAdminActions() {
        @Override
        public void bank(CommandActor actor, String label, String player) {
            calls.add("bank " + player);
        }

        @Override
        public void reset(CommandActor actor, String label, String player, boolean confirmed) {
            calls.add("reset " + player + " " + confirmed);
        }

        @Override
        public void history(CommandActor actor, String label, String player, int page) {
            calls.add("history " + player + " " + page);
        }
    }, (actor, label, target, argument, confirmed) -> calls.add("migrate " + target + " " + argument + " " + confirmed),
            new FakeCompletions(), TestLanguages::french);

    @Test
    void les_sous_commandes_sont_aiguillees_sans_tenir_compte_de_la_casse() {
        RecordingActor admin = new RecordingActor(true);

        router.execute(admin, "novabank", new String[] {"diagnostic"});
        router.execute(admin, "novabank", new String[] {"RELOAD"});
        router.execute(admin, "novabank", new String[] {"bank", "Yo"});
        router.execute(admin, "novabank", new String[] {"reset", "Yo"});
        router.execute(admin, "novabank", new String[] {"reset", "Yo", "CONFIRM"});
        router.execute(admin, "novabank", new String[] {"history", "Yo"});
        router.execute(admin, "novabank", new String[] {"history", "Yo", "3"});

        assertThat(calls).containsExactly("diagnostic", "reload", "bank Yo", "reset Yo false", "reset Yo true",
                "history Yo 1", "history Yo 3");
        assertThat(admin.received()).isEmpty();
    }

    @Test
    void sans_permission_rien_n_est_execute() {
        RecordingActor player = new RecordingActor(false);

        router.execute(player, "novabank", new String[] {"reload"});

        assertThat(calls).isEmpty();
        assertThat(player.received()).singleElement()
                .satisfies(message -> assertThat(message).contains("pas la permission"));
        assertThat(router.complete(player, new String[] {""})).isEmpty();
    }

    @Test
    void le_reset_exige_sa_permission_dediee() {
        RecordingActor admin = new RecordingActor(true).deny(Permissions.ADMIN_RESET);

        router.execute(admin, "nb", new String[] {"reset", "Yo"});

        assertThat(calls).isEmpty();
        assertThat(admin.received()).singleElement().satisfies(line -> assertThat(line).contains("pas la permission"));
        assertThat(router.complete(admin, new String[] {"r"})).containsExactly("reload");
    }

    @Test
    void aide_sous_commande_inconnue_et_arguments_invalides() {
        RecordingActor admin = new RecordingActor(true);

        router.execute(admin, "nb", new String[0]);
        router.execute(admin, "nb", new String[] {"vider"});
        router.execute(admin, "nb", new String[] {"bank"});
        router.execute(admin, "nb", new String[] {"reset", "Yo", "oui"});
        router.execute(admin, "nb", new String[] {"history", "Yo", "zero"});

        assertThat(calls).isEmpty();
        assertThat(admin.received()).containsExactly(
                "NovaBank » Utilisation : /nb <admin|bank|config|diagnostic|discord|gui|help|history|items|migrate|reload|reset|resync|transfer>",
                "NovaBank » Sous-commande inconnue : vider. Utilisation : /nb <admin|bank|config|diagnostic|discord|gui|help|history|items|migrate|reload|reset|resync|transfer>",
                "NovaBank » Utilisation : /nb bank <joueur>",
                "NovaBank » Utilisation : /nb reset <joueur> [confirm]",
                "NovaBank » Page invalide : zero.");
    }

    @Test
    void completion_des_sous_commandes_joueurs_et_confirmation() {
        RecordingActor admin = new RecordingActor(true);

        assertThat(router.complete(admin, new String[] {""}))
                .containsExactly("bank", "diagnostic", "history", "migrate", "reload", "reset");
        assertThat(router.complete(admin, new String[] {"R"})).containsExactly("reload", "reset");
        assertThat(router.complete(admin, new String[] {"bank", "y"})).containsExactly("Yo");
        assertThat(router.complete(admin, new String[] {"reset", "Yo", ""})).containsExactly("confirm");
        assertThat(router.complete(admin, new String[] {"reload", ""})).isEmpty();
    }

    @Test
    void migrate_type_insensible_a_la_casse_confirmation_et_permission_dediee() {
        RecordingActor admin = new RecordingActor(true);

        router.execute(admin, "nb", new String[] {"migrate", "Json"});
        router.execute(admin, "nb", new String[] {"migrate", "json", "confirm"});
        router.execute(admin, "nb", new String[] {"migrate", "xml"});
        router.execute(admin, "nb", new String[] {"migrate"});

        assertThat(calls).containsExactly("migrate JSON Json false", "migrate JSON json true");
        assertThat(admin.received()).containsExactly(
                "NovaBank » Type de stockage inconnu : xml. Types : yaml, json, sqlite, mysql, mariadb, mongodb.",
                "NovaBank » Utilisation : /nb migrate <yaml|json|sqlite|mysql|mariadb|mongodb> [confirm]");
        assertThat(router.complete(admin, new String[] {"migrate", "m"})).containsExactly("mariadb", "mongodb",
                "mysql");
        assertThat(router.complete(admin, new String[] {"migrate", "json", ""})).containsExactly("confirm");

        RecordingActor limited = new RecordingActor(true).deny(Permissions.ADMIN_MIGRATE);
        router.execute(limited, "nb", new String[] {"migrate", "json"});
        assertThat(calls).hasSize(2);
        assertThat(limited.received()).singleElement().satisfies(line -> assertThat(line).contains("pas la permission"));
        assertThat(router.complete(limited, new String[] {"m"})).isEmpty();
    }

    @Test
    void sous_commandes_branchees_gui_et_items_visibles_et_executables_avec_leur_permission() {
        AdminSubcommand gui = new AdminSubcommand() {
            @Override
            public String permission() {
                return Permissions.ADMIN_GUI;
            }

            @Override
            public void execute(CommandActor actor, String label, String[] args) {
                calls.add("gui " + String.join(",", args));
            }

            @Override
            public List<String> complete(String[] args) {
                return List.of("save", "cancel");
            }
        };
        AdminCommandRouter withGui = new AdminCommandRouter(new AdminActions() {
            @Override
            public void diagnostic(CommandActor actor) {
            }

            @Override
            public void reload(CommandActor actor) {
            }
        }, new BankAdminActions() {
            @Override
            public void bank(CommandActor actor, String label, String player) {
            }

            @Override
            public void reset(CommandActor actor, String label, String player, boolean confirmed) {
            }

            @Override
            public void history(CommandActor actor, String label, String player, int page) {
            }
        }, (actor, label, target, argument, confirmed) -> { }, new FakeCompletions(), TestLanguages::french, null, null,
                java.util.Map.of("gui", gui));
        RecordingActor allowed = new RecordingActor(true);
        allowed.grant(Permissions.ADMIN_GUI);
        RecordingActor denied = new RecordingActor(true);

        withGui.execute(allowed, "nb", new String[] {"gui", "save"});
        withGui.execute(denied, "nb", new String[] {"gui"});

        assertThat(calls).containsExactly("gui save");
        assertThat(denied.received()).hasSize(1);
        assertThat(withGui.complete(allowed, new String[] {"g"})).containsExactly("gui");
        assertThat(withGui.complete(denied, new String[] {"g"})).isEmpty();
        assertThat(withGui.complete(allowed, new String[] {"gui", "s"})).containsExactly("save");
        assertThat(withGui.complete(allowed, new String[] {"i"})).isEmpty(); // items n'est pas branche
    }

    @Test
    void sans_argument_ou_avec_help_tout_le_monde_voit_l_accueil_mais_rien_d_autre_n_est_ouvert() {
        List<String> shown = new ArrayList<>();
        AdminCommandRouter withHelp = new AdminCommandRouter(new AdminActions() {
            @Override
            public void diagnostic(CommandActor actor) {
                calls.add("diagnostic");
            }

            @Override
            public void reload(CommandActor actor) {
                calls.add("reload");
            }
        }, new BankAdminActions() {
            @Override
            public void bank(CommandActor actor, String label, String player) { }

            @Override
            public void reset(CommandActor actor, String label, String player, boolean confirmed) { }

            @Override
            public void history(CommandActor actor, String label, String player, int page) { }
        }, (actor, label, target, argument, confirmed) -> { }, new FakeCompletions(),
                TestLanguages::french, null, null, java.util.Map.of("help", new AdminSubcommand() {
                    @Override
                    public String permission() {
                        return "novabank.use";
                    }

                    @Override
                    public List<String> complete(String[] args) {
                        return List.of();
                    }

                    @Override
                    public void execute(CommandActor actor, String label, String[] args) {
                        shown.add("accueil");
                    }
                }));
        RecordingActor player = new RecordingActor(false);

        withHelp.execute(player, "novabank", new String[0]);
        withHelp.execute(player, "novabank", new String[] {"HELP"});
        withHelp.execute(player, "novabank", new String[] {"reload"});

        assertThat(shown).containsExactly("accueil", "accueil");
        assertThat(calls).isEmpty();
        assertThat(player.received()).hasSize(1).first().asString().contains("permission");
        assertThat(withHelp.complete(player, new String[] {""})).containsExactly("help");
    }
}
