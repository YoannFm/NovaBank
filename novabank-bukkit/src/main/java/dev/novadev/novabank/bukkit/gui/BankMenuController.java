package dev.novadev.novabank.bukkit.gui;

import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.core.application.BankSnapshot;
import dev.novadev.novabank.core.application.BankView;
import dev.novadev.novabank.core.application.QueryStatus;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * Etat et logique d'une GUI ouverte, sans dependance serveur. Appele sur le thread du
 * joueur uniquement.
 *
 * <p>Ouverture et rafraichissement : relecture asynchrone de la banque puis affichage.
 * Apres chaque depot ou retrait (resultat envoye par le {@link MenuBackend}), la banque est
 * relue. Une seule operation a la fois depuis la GUI : les clics pendant une operation
 * sont ignores (la couche application refuserait de toute facon). Une fois la GUI fermee,
 * les resultats en retard ne l'affichent plus.
 */
public final class BankMenuController {

    private final UUID owner;
    private final MenuLayout layout;
    private final MenuBackend backend;
    private final MenuScreen screen;
    private final Function<BankView, List<String>> failureLines;
    private final Function<ItemKey, List<String>> noRoomLines;
    private final LongSupplier maxPerOperation;
    private BankSnapshot snapshot;
    private StockPage page;
    private boolean busy;
    private boolean closed;

    /**
     * @param owner           joueur proprietaire de la GUI
     * @param layout          disposition
     * @param backend         operations
     * @param screen          ecran
     * @param failureLines    messages d'une consultation en echec
     * @param noRoomLines     messages d'un retrait sans place dans l'inventaire
     * @param maxPerOperation quantite maximale par operation en vigueur
     */
    public BankMenuController(UUID owner, MenuLayout layout, MenuBackend backend, MenuScreen screen,
                              Function<BankView, List<String>> failureLines,
                              Function<ItemKey, List<String>> noRoomLines, LongSupplier maxPerOperation) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.layout = Objects.requireNonNull(layout, "layout");
        this.backend = Objects.requireNonNull(backend, "backend");
        this.screen = Objects.requireNonNull(screen, "screen");
        this.failureLines = Objects.requireNonNull(failureLines, "failureLines");
        this.noRoomLines = Objects.requireNonNull(noRoomLines, "noRoomLines");
        this.maxPerOperation = Objects.requireNonNull(maxPerOperation, "maxPerOperation");
    }

    /**
     * @return disposition
     */
    public MenuLayout layout() {
        return layout;
    }

    /**
     * Lit la banque puis ouvre le coffre.
     */
    public void open() {
        refresh();
    }

    /**
     * @return {@code true} pendant une operation lancee depuis la GUI
     */
    public boolean isBusy() {
        return busy;
    }

    /**
     * @return {@code true} une fois la GUI fermee
     */
    public boolean isClosed() {
        return closed;
    }

    /**
     * Traite un clic dans le coffre.
     *
     * @param slot  case brute cliquee
     * @param click nature du clic
     * @param room  place dans l'inventaire du joueur
     * @return l'action executee
     */
    public MenuAction click(int slot, MenuClick click, InventoryRoom room) {
        if (closed || page == null) {
            return MenuAction.NONE;
        }
        MenuAction action = ClickResolver.resolve(layout, page, slot, click, room, maxPerOperation.getAsLong());
        if (busy && !(action instanceof MenuAction.Close)) {
            // Une operation est en cours : seule la fermeture reste possible (elle ne touche ni a la
            // banque ni a l'inventaire ; le resultat de l'operation arrive dans le chat).
            return MenuAction.NONE;
        }
        if (action instanceof MenuAction.Withdraw withdraw) {
            busy = true;
            backend.withdraw(owner, withdraw.item(), withdraw.quantity(), this::operationDone);
        } else if (action instanceof MenuAction.DepositAll) {
            busy = true;
            backend.depositAll(owner, this::operationDone);
        } else if (action instanceof MenuAction.ShowPage showPage) {
            render(showPage.page());
        } else if (action instanceof MenuAction.ShowPrices) {
            screen.showPrices();
        } else if (action instanceof MenuAction.Close) {
            screen.close();
        } else if (action instanceof MenuAction.NoRoom noRoom) {
            screen.send(noRoomLines.apply(noRoom.item()));
        }
        return action;
    }

    /**
     * La GUI a ete fermee par le joueur ou le serveur.
     */
    public void closed() {
        closed = true;
    }

    /**
     * @param bankId banque
     * @return {@code true} si la GUI affiche cette banque (thread du joueur)
     */
    public boolean showsBank(BankId bankId) {
        return !closed && snapshot != null && BankId.forOwner(snapshot.owner()).equals(bankId);
    }

    /**
     * Relit la banque affichee apres un changement fait ailleurs (autre joueur ou autre serveur), sauf pendant
     * une operation de ce joueur, qui se rafraichit seule a sa fin (thread du joueur).
     */
    public void refreshFromOutside() {
        if (!closed && !busy) {
            refresh();
        }
    }

    private void operationDone() {
        busy = false;
        if (!closed) {
            refresh();
        }
    }

    private void refresh() {
        backend.view(owner, this::onView);
    }

    private void onView(BankView view) {
        if (closed) {
            return;
        }
        if (view.status() != QueryStatus.OK || view.snapshot().isEmpty()) {
            screen.send(failureLines.apply(view));
            if (snapshot == null) {
                closed = true;
            }
            return;
        }
        snapshot = view.snapshot().get();
        render(page == null ? 0 : page.page());
    }

    private void render(int requestedPage) {
        page = StockPage.of(snapshot, requestedPage, layout.pageSize());
        screen.show(new MenuModel(snapshot.owner(), snapshot.value(), page));
    }
}
