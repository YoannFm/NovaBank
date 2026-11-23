package dev.novadev.novabank.bukkit.integration;

import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.operation.OperationType;
import dev.novadev.novabank.bukkit.command.CommandActor;
import dev.novadev.novabank.bukkit.command.CommandRouter;
import dev.novadev.novabank.config.model.CommandLabel;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

/** Sous-commande par CommandExecutor (Towny) et droits par permission. */
class HostExecutorTest {

    private final List<String> calls = new ArrayList<>();
    private final CommandSender sender = (CommandSender) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[] {CommandSender.class}, (proxy, method, args) -> null);
    private final HostExecutor executor = new HostExecutor(new CommandLabel("bank", List.of("banque")), "town",
            new CommandRouter() {
                @Override
                public void execute(CommandActor actor, String label, String[] args) {
                    calls.add(label + " " + String.join(" ", args));
                }

                @Override
                public List<String> complete(CommandActor actor, String[] args) {
                    return List.of(String.join(",", args));
                }
            });

    @Test
    void le_nom_de_la_sous_commande_est_retire_s_il_est_encore_la() {
        executor.onCommand(sender, null, "town", new String[] {"deposit", "stone"});
        executor.onCommand(sender, null, "town", new String[] {"BANQUE", "info"});

        assertThat(calls).containsExactly("town bank deposit stone", "town bank info");
        assertThat(executor.onTabComplete(sender, null, "town", new String[] {"bank", "de"})).containsExactly("de");
    }

    @Test
    void une_permission_ni_donnee_ni_retiree_ne_decide_rien() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000002");
        Map<String, Boolean> set = Map.of("novabank.town.withdraw", true);
        Player player = (Player) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isPermissionSet" -> set.containsKey((String) args[0]);
                    case "hasPermission" -> set.getOrDefault((String) args[0], false);
                    default -> null;
                });
        PermissionRights rights = new PermissionRights("town", uuid -> uuid.equals(id) ? player : null);
        EntityRef town = new EntityRef("town", "ville");

        assertThat(rights.allows(id, town, OperationType.WITHDRAW)).contains(true);
        assertThat(rights.allows(id, town, OperationType.DEPOSIT)).isEmpty();
        assertThat(rights.allows(UUID.randomUUID(), town, OperationType.WITHDRAW)).as("joueur hors ligne").isEmpty();
    }
}
