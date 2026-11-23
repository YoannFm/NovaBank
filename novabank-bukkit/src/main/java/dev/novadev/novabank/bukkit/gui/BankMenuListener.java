package dev.novadev.novabank.bukkit.gui;

import dev.novadev.novabank.bukkit.item.ItemProviders;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;

/**
 * Evenements d'inventaire des GUI de banque.
 *
 * <p>Tant qu'une GUI NovaBank est ouverte (coffre du haut), TOUT clic et tout glisser est
 * annule, dans le coffre comme dans l'inventaire du joueur : clic, majuscule depuis
 * l'inventaire du joueur, double clic de collecte, touches numeriques, jet, main
 * secondaire, mode creatif. L'annulation est posee en premier (LOWEST) et reimposee en
 * dernier (HIGHEST) au cas ou un autre plugin la leverait. Seul un clic dans le coffre,
 * par son proprietaire et sur sa GUI courante, declenche une action.
 *
 * <p>Le coffre du haut est lu par {@link InventoryClickEvent#getInventory()} et non par la
 * vue ({@code InventoryView} est devenue une interface en 1.21 : un appel compile contre
 * 1.17 echouerait).
 */
public final class BankMenuListener implements Listener, AutoCloseable {

    private final BankMenus menus;
    private final ItemProviders providers;

    /**
     * @param menus     GUI ouvertes
     * @param providers fournisseurs d'items (place dans l'inventaire)
     */
    public BankMenuListener(BankMenus menus, ItemProviders providers) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.providers = Objects.requireNonNull(providers, "providers");
    }

    /**
     * @param event clic
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getInventory();
        if (!(top.getHolder() instanceof BankMenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() != top || !(event.getWhoClicked() instanceof Player player)
                || !menus.isCurrent(holder, player.getUniqueId())) {
            return;
        }
        holder.controller().click(event.getRawSlot(), ClickKinds.of(event.getClick()),
                new PlayerInventoryRoom(player, providers));
    }

    /**
     * @param event clic, apres les autres plugins
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void keepClickCancelled(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof BankMenuHolder) {
            event.setCancelled(true);
        }
    }

    /**
     * @param event glisser
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        keepDragCancelled(event);
    }

    /**
     * @param event glisser, apres les autres plugins
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void keepDragCancelled(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof BankMenuHolder) {
            event.setCancelled(true);
        }
    }

    /**
     * @param event fermeture d'un inventaire
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof BankMenuHolder holder) {
            menus.closed(holder);
        }
    }

    /**
     * @param event deconnexion
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        menus.forget(event.getPlayer().getUniqueId());
    }

    @Override
    public void close() {
        HandlerList.unregisterAll(this);
    }
}
