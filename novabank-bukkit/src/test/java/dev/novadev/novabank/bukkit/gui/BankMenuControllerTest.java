package dev.novadev.novabank.bukkit.gui;

import static dev.novadev.novabank.bukkit.gui.MenuFixtures.PLAYER;
import static dev.novadev.novabank.bukkit.gui.MenuFixtures.STONE;
import static dev.novadev.novabank.bukkit.gui.MenuFixtures.room;
import static dev.novadev.novabank.bukkit.gui.MenuFixtures.settings;
import static dev.novadev.novabank.bukkit.gui.MenuFixtures.snapshot;
import static dev.novadev.novabank.bukkit.gui.MenuFixtures.stoneBank;
import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.api.entity.BankRole;
import dev.novadev.novabank.api.entity.EntityMembership;
import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.core.application.BankSnapshot;
import dev.novadev.novabank.core.application.BankView;
import dev.novadev.novabank.core.application.QueryStatus;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/**
 * Ouverture, rafraichissement apres chaque operation, une operation a la fois, GUI fermee
 * insensible aux resultats en retard.
 */
class BankMenuControllerTest {

    private final FakeBackend backend = new FakeBackend();
    private final FakeScreen screen = new FakeScreen();
    private final BankMenuController controller = new BankMenuController(PLAYER, new MenuLayout(settings(2)), backend,
            screen, view -> List.of("echec " + view.status()), item -> List.of("plein " + item), () -> 2304L);

    @Test
    void ouverture_lit_la_banque_puis_affiche_la_premiere_page() {
        controller.open();
        assertThat(screen.shown).isEmpty();

        backend.answerView(ok(stoneBank(100)));

        assertThat(screen.shown).singleElement().satisfies(model -> {
            assertThat(model.page().entries()).singleElement()
                    .satisfies(entry -> assertThat(entry.quantity()).isEqualTo(100));
            assertThat(model.value()).isEqualByComparingTo("100");
        });
    }

    @Test
    void retrait_puis_relecture_et_mise_a_jour_de_l_affichage() {
        open(stoneBank(100));

        MenuAction action = controller.click(0, MenuClick.LEFT, room(1000));

        assertThat(action).isEqualTo(new MenuAction.Withdraw(STONE, 64));
        assertThat(backend.calls).containsExactly("view", "withdraw minecraft:stone 64");
        assertThat(controller.isBusy()).isTrue();
        assertThat(controller.click(0, MenuClick.RIGHT, room(1000))).as("une operation a la fois")
                .isEqualTo(MenuAction.NONE);
        backend.finishOperation();
        assertThat(controller.isBusy()).isFalse();
        assertThat(backend.calls).containsExactly("view", "withdraw minecraft:stone 64", "view");
        backend.answerView(ok(stoneBank(36)));
        assertThat(screen.shown).hasSize(2);
        assertThat(screen.shown.get(1).page().entries().get(0).quantity()).isEqualTo(36);
    }

    @Test
    void deposer_tout_puis_relecture() {
        open(stoneBank(1));

        controller.click(13, MenuClick.LEFT, room(0));
        backend.finishOperation();

        assertThat(backend.calls).containsExactly("view", "depositAll", "view");
    }

    @Test
    void changement_de_page_sans_relecture_et_page_conservee_au_rafraichissement() {
        open(snapshot(20));

        controller.click(14, MenuClick.LEFT, room(0));

        assertThat(screen.shown).hasSize(2);
        assertThat(screen.shown.get(1).page().page()).isEqualTo(1);
        assertThat(backend.calls).containsExactly("view");
        controller.click(0, MenuClick.RIGHT, room(10));
        backend.finishOperation();
        backend.answerView(ok(snapshot(12)));
        assertThat(screen.shown.get(2).page().page()).isEqualTo(1);
        controller.click(12, MenuClick.LEFT, room(0));
        assertThat(screen.shown.get(screen.shown.size() - 1).page().page()).isZero();
    }

    @Test
    void fermeture_par_bouton_et_message_inventaire_plein() {
        open(stoneBank(5));

        controller.click(17, MenuClick.LEFT, room(0));
        controller.click(0, MenuClick.SHIFT, room(0));

        assertThat(screen.closeRequests).isEqualTo(1);
        assertThat(screen.sent).containsExactly("plein minecraft:stone");
    }

    @Test
    void le_livre_ouvre_la_liste_des_prix() {
        open(stoneBank(5));

        assertThat(controller.click(9, MenuClick.LEFT, room(0))).isEqualTo(new MenuAction.ShowPrices());
        assertThat(screen.priceRequests).isEqualTo(1);
        assertThat(backend.calls).containsExactly("view");
    }

    @Test
    void le_bouton_fermer_reste_actif_pendant_une_operation_en_cours() {
        open(stoneBank(5));
        controller.click(13, MenuClick.LEFT, room(0));
        assertThat(controller.isBusy()).isTrue();

        MenuAction action = controller.click(17, MenuClick.LEFT, room(0));

        assertThat(action).isInstanceOf(MenuAction.Close.class);
        assertThat(screen.closeRequests).isEqualTo(1);
        assertThat(controller.click(0, MenuClick.LEFT, room(10))).as("pas d'autre action pendant l'operation")
                .isEqualTo(MenuAction.NONE);
        assertThat(backend.calls).containsExactly("view", "depositAll");
    }

    @Test
    void echec_a_l_ouverture_message_sans_affichage_et_gui_inerte() {
        controller.open();
        backend.answerView(failed(QueryStatus.NO_BANK));

        assertThat(screen.sent).containsExactly("echec NO_BANK");
        assertThat(screen.shown).isEmpty();
        assertThat(controller.isClosed()).isTrue();
        assertThat(controller.click(0, MenuClick.LEFT, room(10))).isEqualTo(MenuAction.NONE);
    }

    @Test
    void echec_d_un_rafraichissement_garde_l_affichage_precedent() {
        open(stoneBank(5));
        controller.click(0, MenuClick.RIGHT, room(10));
        backend.finishOperation();

        backend.answerView(failed(QueryStatus.STORAGE_ERROR));

        assertThat(screen.sent).containsExactly("echec STORAGE_ERROR");
        assertThat(controller.isClosed()).isFalse();
        assertThat(screen.shown).hasSize(1);
    }

    @Test
    void gui_fermee_les_resultats_en_retard_ne_reaffichent_rien() {
        open(stoneBank(100));
        controller.click(0, MenuClick.LEFT, room(1000));

        controller.closed();
        backend.finishOperation();

        assertThat(backend.calls).containsExactly("view", "withdraw minecraft:stone 64");
        assertThat(controller.click(0, MenuClick.LEFT, room(1000))).isEqualTo(MenuAction.NONE);
        assertThat(screen.shown).hasSize(1);
    }

    @Test
    void clic_avant_le_premier_affichage_ignore() {
        controller.open();

        assertThat(controller.click(0, MenuClick.LEFT, room(10))).isEqualTo(MenuAction.NONE);
    }

    private void open(BankSnapshot snapshot) {
        controller.open();
        backend.answerView(ok(snapshot));
    }

    private static BankView failed(QueryStatus status) {
        return new BankView(status, Optional.empty(), List.of(), Optional.empty());
    }

    private static BankView ok(BankSnapshot snapshot) {
        EntityMembership membership = new EntityMembership(snapshot.owner(), BankRole.OWNER);
        return new BankView(QueryStatus.OK, Optional.of(membership), List.of(membership), Optional.of(snapshot));
    }

    private static final class FakeBackend implements MenuBackend {

        final List<String> calls = new ArrayList<>();
        private final Deque<Consumer<BankView>> views = new ArrayDeque<>();
        private Runnable operation;

        @Override
        public void view(UUID playerId, Consumer<BankView> done) {
            calls.add("view");
            views.add(done);
        }

        @Override
        public void withdraw(UUID playerId, ItemKey item, long quantity, Runnable done) {
            calls.add("withdraw " + item + " " + quantity);
            operation = done;
        }

        @Override
        public void depositAll(UUID playerId, Runnable done) {
            calls.add("depositAll");
            operation = done;
        }

        void answerView(BankView view) {
            views.poll().accept(view);
        }

        void finishOperation() {
            operation.run();
        }
    }

    private static final class FakeScreen implements MenuScreen {

        final List<MenuModel> shown = new ArrayList<>();
        final List<String> sent = new ArrayList<>();
        int closeRequests;
        int priceRequests;

        @Override
        public void show(MenuModel model) {
            shown.add(model);
        }

        @Override
        public void close() {
            closeRequests++;
        }

        @Override
        public void showPrices() {
            priceRequests++;
        }

        @Override
        public void send(List<String> lines) {
            sent.addAll(lines);
        }
    }
}
