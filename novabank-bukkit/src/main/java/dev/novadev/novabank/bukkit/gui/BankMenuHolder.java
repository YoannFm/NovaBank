package dev.novadev.novabank.bukkit.gui;

import dev.novadev.novabank.config.model.GuiSettings;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Coffre d'une GUI de banque ouverte pour un joueur : identifie le coffre dans les
 * evenements d'inventaire et affiche le contenu calcule par le {@link BankMenuController}.
 * Thread du joueur.
 */
public final class BankMenuHolder implements InventoryHolder, MenuScreen {

    /** Creation du coffre (seul appel de l'API d'inventaire avec un titre texte). */
    @FunctionalInterface
    public interface InventoryFactory {

        /**
         * @param holder proprietaire du coffre
         * @param size   nombre de cases
         * @param title  titre
         * @return le coffre
         */
        Inventory create(InventoryHolder holder, int size, String title);
    }

    private final UUID owner;
    private final Function<UUID, Player> players;
    private final InventoryFactory inventories;
    private final MenuRenderer renderer;
    private final GuiSettings settings;
    private final Runnable closeLater;
    private final Runnable pricesLater;
    private BankMenuController controller;
    private Inventory inventory;

    /**
     * @param owner       joueur
     * @param players     joueur connecte par UUID
     * @param inventories creation du coffre
     * @param renderer    rendu
     * @param settings    disposition et icones
     * @param closeLater  fermeture du coffre du joueur au tick suivant
     * @param pricesLater liste des prix a la place du coffre, au tick suivant
     */
    BankMenuHolder(UUID owner, Function<UUID, Player> players, InventoryFactory inventories, MenuRenderer renderer,
                   GuiSettings settings, Runnable closeLater, Runnable pricesLater) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.players = Objects.requireNonNull(players, "players");
        this.inventories = Objects.requireNonNull(inventories, "inventories");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.closeLater = Objects.requireNonNull(closeLater, "closeLater");
        this.pricesLater = Objects.requireNonNull(pricesLater, "pricesLater");
    }

    void attach(BankMenuController attached) {
        this.controller = Objects.requireNonNull(attached, "controller");
    }

    /**
     * @return logique de la GUI
     */
    BankMenuController controller() {
        return controller;
    }

    /**
     * @return joueur proprietaire
     */
    UUID owner() {
        return owner;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    @Override
    public void show(MenuModel model) {
        Player player = players.apply(owner);
        if (player == null) {
            controller.closed();
            return;
        }
        boolean first = inventory == null;
        if (first) {
            inventory = inventories.create(this, controller.layout().size(), renderer.title(model));
        }
        inventory.setContents(renderer.render(model, controller.layout(), settings));
        if (first) {
            player.openInventory(inventory);
        }
    }

    @Override
    public void close() {
        closeLater.run();
    }

    @Override
    public void showPrices() {
        pricesLater.run();
    }

    @Override
    public void send(List<String> lines) {
        Player player = players.apply(owner);
        if (player != null) {
            lines.forEach(player::sendMessage);
        }
    }
}
