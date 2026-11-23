package dev.novadev.novabank.bukkit.gui;

import dev.novadev.novabank.config.model.GuiSettings;
import dev.novadev.novabank.core.application.BankView;
import dev.novadev.novabank.core.application.TaskScheduler;
import dev.novadev.novabank.core.text.ConsoleText;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.entity.Player;

/**
 * GUI de banque ouvertes, une au plus par joueur : ouverture, GUI courante (une GUI
 * remplacee ou fermee ne declenche plus rien), fermeture de toutes les GUI a l'arret du
 * plugin (sinon leurs icones deviendraient prenables). Ouverture et evenements : thread du
 * joueur (thread principal sous Bukkit, region du joueur sous Folia).
 *
 * <p>A l'arret, une GUI n'est fermee que si son joueur est manipulable depuis le thread qui
 * desactive le plugin : toujours sous Bukkit (thread principal) et a l'arret du serveur
 * Folia (le thread d'arret possede alors toutes les regions, deja arretees : aucun clic ne
 * peut plus etre traite). Une desactivation a chaud sous Folia depuis un autre thread ne
 * peut pas fermer les GUI des autres regions : elle est signalee.
 */
public final class BankMenus implements AutoCloseable {

    private final Map<UUID, BankMenuHolder> open = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> refreshing = ConcurrentHashMap.newKeySet();
    private final Function<UUID, Player> players;
    private final BankMenuHolder.InventoryFactory inventories;
    private final MenuRenderer renderer;
    private final MenuBackend backend;
    private final Supplier<GuiSettings> settings;
    private final Function<BankView, List<String>> failureLines;
    private final LongSupplier maxPerOperation;
    private final TaskScheduler scheduler;
    private final Predicate<UUID> accessibleHere;
    private final Consumer<Player> prices;
    private final Logger logger;

    /**
     * @param players         joueur connecte par UUID
     * @param inventories     creation des coffres
     * @param renderer        rendu
     * @param backend         operations
     * @param settings        disposition en vigueur
     * @param failureLines    messages d'une consultation en echec
     * @param maxPerOperation quantite maximale par operation en vigueur
     * @param scheduler       planificateur (fermeture differee d'un tick)
     * @param accessibleHere  joueur connecte et manipulable depuis le thread appelant
     * @param prices          ouvre la liste des items acceptes et de leur prix (bouton Information)
     * @param logger          journal (GUI non fermees a l'arret)
     */
    public BankMenus(Function<UUID, Player> players, BankMenuHolder.InventoryFactory inventories,
                     MenuRenderer renderer, MenuBackend backend, Supplier<GuiSettings> settings,
                     Function<BankView, List<String>> failureLines, LongSupplier maxPerOperation,
                     TaskScheduler scheduler, Predicate<UUID> accessibleHere, Consumer<Player> prices,
                     Logger logger) {
        this.players = Objects.requireNonNull(players, "players");
        this.inventories = Objects.requireNonNull(inventories, "inventories");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.backend = Objects.requireNonNull(backend, "backend");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.failureLines = Objects.requireNonNull(failureLines, "failureLines");
        this.maxPerOperation = Objects.requireNonNull(maxPerOperation, "maxPerOperation");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.accessibleHere = Objects.requireNonNull(accessibleHere, "accessibleHere");
        this.prices = Objects.requireNonNull(prices, "prices");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Ouvre la GUI d'un joueur connecte (remplace la precedente).
     *
     * @param playerId joueur
     */
    public void open(UUID playerId) {
        GuiSettings current = settings.get();
        BankMenuHolder holder = new BankMenuHolder(playerId, players, inventories, renderer, current,
                () -> leaveLater(playerId, Player::closeInventory), () -> leaveLater(playerId, prices));
        BankMenuController controller = new BankMenuController(playerId, new MenuLayout(current), backend, holder,
                failureLines, renderer::noRoom, maxPerOperation);
        holder.attach(controller);
        BankMenuHolder previous = open.put(playerId, holder);
        if (previous != null) {
            previous.controller().closed();
        }
        controller.open();
    }

    /**
     * Rafraichit les GUI ouvertes qui affichent la banque modifiee (par un autre joueur ou un autre serveur). Une
     * seule relecture en attente par joueur ; appele depuis n'importe quel thread.
     *
     * @param change banque modifiee
     */
    public void refreshViewers(dev.novadev.novabank.core.sync.BankChange change) {
        for (BankMenuHolder holder : List.copyOf(open.values())) {
            UUID viewer = holder.owner();
            if (!refreshing.add(viewer)) {
                continue;
            }
            try {
                scheduler.runForPlayer(viewer, () -> {
                    refreshing.remove(viewer);
                    BankMenuHolder current = open.get(viewer);
                    if (current == holder && current.controller().showsBank(change.bankId())) {
                        current.controller().refreshFromOutside();
                    }
                }, () -> refreshing.remove(viewer));
            } catch (RuntimeException rejected) {
                refreshing.remove(viewer);
            }
        }
    }

    /**
     * @param holder  coffre de l'evenement
     * @param clicker joueur a l'origine de l'evenement
     * @return {@code true} si ce coffre est la GUI courante de ce joueur et qu'il en est le proprietaire
     */
    boolean isCurrent(BankMenuHolder holder, UUID clicker) {
        return holder.owner().equals(clicker) && open.get(clicker) == holder && !holder.controller().isClosed();
    }

    /**
     * @param holder coffre ferme
     */
    void closed(BankMenuHolder holder) {
        holder.controller().closed();
        open.remove(holder.owner(), holder);
    }

    /**
     * @param playerId joueur deconnecte
     */
    void forget(UUID playerId) {
        BankMenuHolder holder = open.remove(playerId);
        if (holder != null) {
            holder.controller().closed();
        }
    }

    @Override
    public void close() {
        int notClosed = 0;
        for (BankMenuHolder holder : List.copyOf(open.values())) {
            forget(holder.owner());
            if (holder.getInventory() == null) {
                continue;
            }
            if (!accessibleHere.test(holder.owner())) {
                notClosed++;
                continue;
            }
            Player player = players.apply(holder.owner());
            if (player != null) {
                player.closeInventory();
            }
        }
        if (notClosed > 0) {
            logger.warning(
                    ConsoleText.tr("{0} GUI NovaBank non fermee(s) a la desactivation : joueurs geres par un autre thread (desactivation a chaud sous Folia)",
                            notClosed));
        }
    }

    /**
     * Quitte la GUI au tick suivant (on ne change pas de coffre pendant l'evenement de clic), si elle est toujours celle
     * du joueur : {@code next} ferme le coffre ou en ouvre un autre.
     */
    private void leaveLater(UUID playerId, Consumer<Player> next) {
        BankMenuHolder holder = open.get(playerId);
        scheduler.runForPlayer(playerId, () -> {
            Player player = players.apply(playerId);
            if (player != null && holder != null && open.get(playerId) == holder) {
                forget(playerId);
                next.accept(player);
            }
        }, () -> forget(playerId));
    }
}
