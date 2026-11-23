package dev.novadev.novabank.bukkit.command;

import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.bukkit.testing.TestLanguages;
import dev.novadev.novabank.core.access.Permissions;
import org.junit.jupiter.api.Test;

class HelpCommandTest {

    private final HelpCommand help = new HelpCommand(TestLanguages::french, "YoannFM", "2026.10.29 (1.0.0 #1)", "bank");

    @Test
    void l_accueil_donne_auteur_version_description_et_liens_dans_l_ordre() {
        RecordingActor player = new RecordingActor(false).grant(Permissions.DEPOSIT).grant(Permissions.WITHDRAW);

        help.execute(player, "novabank", new String[0]);

        String all = String.join("\n", player.received());
        assertThat(all.indexOf("YoannFM")).isLessThan(all.indexOf("v2026.10.29 (1.0.0 #1)"));
        assertThat(all.indexOf("v2026.10.29")).isLessThan(all.indexOf("Description"));
        assertThat(all.indexOf("Description")).isLessThan(all.indexOf(HelpCommand.DISCORD));
        assertThat(all.indexOf(HelpCommand.DISCORD)).isLessThan(all.indexOf(HelpCommand.GITHUB));
    }

    @Test
    void un_joueur_sans_droit_admin_ne_voit_que_les_commandes_qu_il_peut_utiliser() {
        RecordingActor player = new RecordingActor(false).grant(Permissions.DEPOSIT).grant(Permissions.WITHDRAW);

        help.execute(player, "novabank", new String[0]);

        String all = String.join("\n", player.received());
        assertThat(all).contains("/bank deposit <item|hand|all> [quantite]", "/novabank help")
                .doesNotContain("/novabank reload", "/novabank config", "/bank admin");
    }

    @Test
    void un_administrateur_voit_tout_et_chaque_ligne_pre_remplit_sa_commande() {
        RecordingActor admin = new RecordingActor(true).grant(Permissions.DEPOSIT).grant(Permissions.WITHDRAW)
                .grant(Permissions.ADMIN_CONFIG).grant(Permissions.ADMIN_GUI).grant(Permissions.ADMIN_ITEMS)
                .grant(Permissions.ADMIN_TRANSFER);

        help.execute(admin, "novabank", new String[0]);

        String all = String.join("\n", admin.received());
        assertThat(all).contains("/novabank reload", "/novabank config <get|set|push>", "/novabank items",
                "/bank admin", "/novabank transfer");
        assertThat(admin.suggestions()).contains("/bank deposit ", "/novabank reload", "/novabank history ");
        assertThat(admin.links()).containsExactly(HelpCommand.DISCORD, HelpCommand.GITHUB);
    }
}
