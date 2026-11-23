package dev.novadev.novabank.bukkit.host;

import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.operation.OperationType;
import dev.novadev.novabank.bukkit.command.CommandActor;
import dev.novadev.novabank.bukkit.command.CommandRouter;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.Test;

/** Pont a distance : commande publiee par le moteur, droits publies par le JAR de l'edition. */
class RemoteBridgesTest {

    private final ServicesManager services = FakeServices.create();
    private final List<String> calls = new ArrayList<>();
    private final CommandSender sender = (CommandSender) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[] {CommandSender.class}, (proxy, method, args) -> null);

    @Test
    void la_commande_publiee_est_retrouvee_par_son_edition_puis_retiree() throws Exception {
        AutoCloseable published = RemoteBridges.publishCommands(services, null, "bentobox", new CommandRouter() {
            @Override
            public void execute(CommandActor actor, String label, String[] args) {
                calls.add(label + " " + String.join(" ", args));
            }

            @Override
            public List<String> complete(CommandActor actor, String[] args) {
                return List.of("deposit");
            }
        });

        assertThat(RemoteBridges.commands(services, "towny")).isEmpty();
        RemoteBridges.commands(services, "bentobox").orElseThrow()
                .onCommand(sender, null, "is bank", new String[] {"deposit", "stone", "5"});
        assertThat(calls).containsExactly("is bank deposit stone 5");
        assertThat(RemoteBridges.commands(services, "bentobox").orElseThrow()
                .onTabComplete(sender, null, "is bank", new String[] {"de"})).containsExactly("deposit");

        published.close();
        assertThat(RemoteBridges.commands(services, "bentobox")).isEmpty();
    }

    @Test
    void les_droits_publies_par_l_edition_sont_lus_a_chaque_operation() throws Exception {
        UUID player = UUID.fromString("00000000-0000-0000-0000-000000000001");
        EntityRef island = new EntityRef("island", "ile-1");
        var rights = RemoteBridges.rights(services, "bentobox");
        assertThat(rights.allows(player, island, OperationType.WITHDRAW)).as("rien de publie").isEmpty();

        List<String> queries = new ArrayList<>();
        AutoCloseable published = RemoteBridges.publishRights(services, null, "bentobox", query -> {
            queries.add(query);
            return Optional.of(query.endsWith("DEPOSIT"));
        });

        assertThat(rights.allows(player, island, OperationType.DEPOSIT)).contains(true);
        assertThat(rights.allows(player, island, OperationType.WITHDRAW)).contains(false);
        assertThat(queries).containsExactly(player + ";ile-1;DEPOSIT", player + ";ile-1;WITHDRAW");

        published.close();
        assertThat(rights.allows(player, island, OperationType.DEPOSIT)).isEmpty();
    }
}
