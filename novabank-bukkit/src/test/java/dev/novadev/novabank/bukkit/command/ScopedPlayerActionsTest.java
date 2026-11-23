package dev.novadev.novabank.bukkit.command;

import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.bukkit.testing.TestLanguages;
import dev.novadev.novabank.core.application.Amount;
import dev.novadev.novabank.core.application.ItemChoice;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Commande montee dans un hote ({@code /is bank}) : banque du type impose, ni select ni admin. */
class ScopedPlayerActionsTest {

    private final List<String> calls = new ArrayList<>();
    private boolean member = true;

    private final PlayerActions delegate = new PlayerActions() {
        @Override
        public void deposit(CommandActor actor, UUID playerId, ItemChoice choice, Amount amount) {
            calls.add("deposit " + choice.item().map(ItemKey::toString).orElse("-"));
        }

        @Override
        public void withdraw(CommandActor actor, UUID playerId, ItemKey item, Amount amount) {
            calls.add("withdraw " + item);
        }

        @Override
        public void info(CommandActor actor, UUID playerId, String label) {
            calls.add("info " + label);
        }

        @Override
        public void select(CommandActor actor, UUID playerId, String entityType) {
            calls.add("select " + entityType);
        }

        @Override
        public void open(CommandActor actor, UUID playerId) {
            calls.add("open");
        }

        @Override
        public void top(CommandActor actor, UUID playerId, String entityType) {
            calls.add("top " + entityType);
        }
    };

    private final ScopedPlayerActions.Selector selector = (actor, playerId, type, command, then) -> {
        calls.add("active " + type + " " + command);
        if (member) {
            then.run();
        } else {
            actor.send("pas d'ile");
        }
    };

    private final PlayerCommandRouter router = PlayerCommandRouter.scoped(
            new ScopedPlayerActions(delegate, selector, "island", "is bank"), new FakeCompletions(), TestLanguages::french);
    private final RecordingActor player = new RecordingActor(true);

    @Test
    void chaque_action_rend_d_abord_active_la_banque_du_type() {
        router.execute(player, "is bank", new String[0]);
        router.execute(player, "is bank", new String[] {"deposit", "stone", "5"});
        router.execute(player, "is bank", new String[] {"withdraw", "stone", "1"});
        router.execute(player, "is bank", new String[] {"info"});
        router.execute(player, "is bank", new String[] {"top"});

        assertThat(calls).containsExactly("active island is bank", "open", "active island is bank", "deposit minecraft:stone",
                "active island is bank", "withdraw minecraft:stone", "active island is bank", "info is bank", "top island");
    }

    @Test
    void sans_entite_du_type_rien_n_est_lance() {
        member = false;
        router.execute(player, "is bank", new String[] {"deposit", "stone", "5"});

        assertThat(calls).containsExactly("active island is bank");
        assertThat(player.received()).containsExactly("pas d'ile");
    }

    @Test
    void select_et_admin_n_existent_pas_dans_la_commande_d_un_hote() {
        router.execute(player, "is bank", new String[] {"select", "town"});
        router.execute(player, "is bank", new String[] {"admin"});

        assertThat(calls).isEmpty();
        assertThat(player.received()).hasSize(2).allSatisfy(line -> assertThat(line).contains("Sous-commande inconnue"));
        assertThat(router.complete(player, new String[] {""})).doesNotContain("select", "admin").contains("deposit");
    }
}
