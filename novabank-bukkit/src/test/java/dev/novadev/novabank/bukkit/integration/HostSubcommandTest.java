package dev.novadev.novabank.bukkit.integration;

import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.bukkit.command.CommandActor;
import dev.novadev.novabank.bukkit.command.CommandRouter;
import dev.novadev.novabank.config.model.CommandLabel;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

/** Sous-commande montee dans un hote par Proxy, sur une interface aux memes methodes que SuperiorCommand. */
class HostSubcommandTest {

    /** Copie des methodes de {@code SuperiorCommand} (SuperiorSkyblock2 2026.3). */
    public interface FakeSuperiorCommand {
        List<String> getAliases();

        String getPermission();

        String getUsage(Locale locale);

        String getDescription(Locale locale);

        int getMinArgs();

        int getMaxArgs();

        boolean canBeExecutedByConsole();

        boolean displayCommand();

        void execute(Object plugin, CommandSender sender, String[] args);

        List<String> tabComplete(Object plugin, CommandSender sender, String[] args);
    }

    private final List<String> calls = new ArrayList<>();
    private final CommandRouter router = new CommandRouter() {
        @Override
        public void execute(CommandActor actor, String label, String[] args) {
            calls.add(label + " " + String.join(" ", args));
        }

        @Override
        public List<String> complete(CommandActor actor, String[] args) {
            return List.of("complete:" + String.join(",", args));
        }
    };

    private final CommandSender sender = (CommandSender) java.lang.reflect.Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[] {CommandSender.class}, (proxy, method, args) -> null);

    private final FakeSuperiorCommand command = (FakeSuperiorCommand) new HostSubcommand(
            new CommandLabel("coffre", List.of("banque")), "is", router, "Banque").proxy(FakeSuperiorCommand.class);

    @Test
    void nom_alias_et_bornes_annonces_a_l_hote() {
        assertThat(command.getAliases()).containsExactly("coffre", "banque");
        assertThat(command.getPermission()).isEqualTo("novabank.use");
        assertThat(command.getMinArgs()).isEqualTo(1);
        assertThat(command.getMaxArgs()).isEqualTo(4);
        assertThat(command.canBeExecutedByConsole()).isFalse();
        assertThat(command.getUsage(Locale.FRENCH)).startsWith("coffre ");
        assertThat(command.toString()).isEqualTo("NovaBank /is coffre");
        assertThat(command).isEqualTo(command);
    }

    @Test
    void le_nom_tape_est_retire_des_arguments_et_sert_d_etiquette() {
        command.execute(null, sender, new String[] {"banque", "deposit", "stone", "5"});
        command.execute(null, sender, new String[] {"coffre"});

        assertThat(calls).containsExactly("is banque deposit stone 5", "is coffre ");
        assertThat(command.tabComplete(null, sender, new String[] {"coffre", "dep"})).containsExactly("complete:dep");
    }
}
