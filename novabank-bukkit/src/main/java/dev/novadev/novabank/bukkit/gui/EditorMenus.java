package dev.novadev.novabank.bukkit.gui;

import dev.novadev.novabank.api.item.ItemKey;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.ItemStack;

/**
 * Coffres des ecrans d'edition ({@link EditorScreen}) et de la liste des prix des joueurs ({@link PriceListScreen}), un au plus
 * par joueur. Comme la GUI de banque, tout clic et tout
 * glisser est annule tant que le coffre est ouvert ; seul un clic dans le coffre, par son proprietaire, est transmis a
 * l'ecran, qui est ensuite redessine. Ouverture et evenements : thread du joueur.
 */
public final class EditorMenus implements Listener, AutoCloseable {

    private static final class EditorHolder implements InventoryHolder {

        private final UUID owner;
        private final EditorScreen screen;
        private final Runnable onClose;
        private Inventory inventory;

        EditorHolder(UUID owner, EditorScreen screen, Runnable onClose) {
            this.owner = owner;
            this.screen = screen;
            this.onClose = onClose;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    /** Icone d'un item que ce serveur ne connait pas (plugin fournisseur absent). */
    private static final ItemKey UNKNOWN_ICON = ItemKey.minecraft("barrier");

    private final Map<UUID, EditorHolder> open = new ConcurrentHashMap<>();
    private final Map<UUID, Consumer<String>> prompts = new ConcurrentHashMap<>();
    private final BankMenuHolder.InventoryFactory inventories;
    private final Function<ItemKey, Optional<ItemStack>> icons;
    private final Function<ItemStack, Optional<ItemKey>> identify;
    private final Function<UUID, Player> players;
    private final Predicate<UUID> accessibleHere;

    /**
     * @param inventories    creation des coffres
     * @param icons          icone d'un item (vide si l'item n'existe pas sur ce serveur)
     * @param identify       item d'un ItemStack (item tenu en main)
     * @param players        joueur connecte par UUID
     * @param accessibleHere joueur manipulable depuis le thread appelant (fermeture a l'arret)
     */
    public EditorMenus(BankMenuHolder.InventoryFactory inventories, Function<ItemKey, Optional<ItemStack>> icons,
                       Function<ItemStack, Optional<ItemKey>> identify, Function<UUID, Player> players,
                       Predicate<UUID> accessibleHere) {
        this.inventories = Objects.requireNonNull(inventories, "inventories");
        this.icons = Objects.requireNonNull(icons, "icons");
        this.identify = Objects.requireNonNull(identify, "identify");
        this.players = Objects.requireNonNull(players, "players");
        this.accessibleHere = Objects.requireNonNull(accessibleHere, "accessibleHere");
    }

    /**
     * Ouvre un ecran pour un joueur (remplace le precedent).
     *
     * @param player  administrateur
     * @param screen  ecran
     * @param onClose appele a la fermeture du coffre (par le joueur, un autre menu ou la deconnexion)
     */
    public void open(Player player, EditorScreen screen, Runnable onClose) {
        prompts.remove(player.getUniqueId());
        EditorHolder holder = new EditorHolder(player.getUniqueId(), screen, onClose);
        holder.inventory = inventories.create(holder, screen.size(), screen.title());
        draw(holder);
        open.put(player.getUniqueId(), holder);
        player.openInventory(holder.inventory);
    }

    /**
     * Ferme le coffre SANS declencher son retour (fermeture voulue) et donne la prochaine ligne de chat du joueur a
     * {@code reply} (thread du chat : le recepteur change de thread lui-meme). Un nouveau coffre annule la saisie.
     *
     * @param player administrateur
     * @param reply  recoit la ligne tapee
     */
    public void prompt(Player player, Consumer<String> reply) {
        open.remove(player.getUniqueId());
        prompts.put(player.getUniqueId(), reply);
        player.closeInventory();
    }

    /**
     * @param event ligne de chat
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Consumer<String> reply = prompts.remove(event.getPlayer().getUniqueId());
        if (reply != null) {
            event.setCancelled(true);
            reply.accept(event.getMessage());
        }
    }

    /**
     * @param playerId administrateur
     * @return {@code true} s'il a un coffre d'edition ouvert
     */
    public boolean isOpen(UUID playerId) {
        return open.containsKey(playerId);
    }

    /**
     * Redessine le coffre d'un administrateur (l'etat de l'ecran a change hors d'un clic, par exemple apres un
     * enregistrement termine).
     *
     * @param playerId administrateur
     */
    public void refresh(UUID playerId) {
        EditorHolder holder = open.get(playerId);
        if (holder != null) {
            draw(holder);
        }
    }

    private void draw(EditorHolder holder) {
        for (int slot = 0; slot < holder.screen.size(); slot++) {
            ItemStack stack = holder.screen.slot(slot).flatMap(view -> icons.apply(view.icon()).or(() -> icons.apply(UNKNOWN_ICON))
                    .map(icon -> ItemTexts.describe(icon.clone(), view.name(), view.lore()))).orElse(null);
            holder.inventory.setItem(slot, stack);
        }
    }

    private Optional<ItemKey> inHand(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        return held == null || held.getType() == Material.AIR ? Optional.empty() : identify.apply(held);
    }

    /**
     * @param event clic
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof EditorHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.owner.equals(player.getUniqueId())
                || open.get(holder.owner) != holder) {
            return;
        }
        if (event.getClickedInventory() == event.getInventory()) {
            holder.screen.click(event.getRawSlot(), ClickKinds.of(event.getClick()), inHand(player));
        } else if (event.getClickedInventory() instanceof PlayerInventory && event.getCurrentItem() != null
                && event.getCurrentItem().getType() != Material.AIR) {
            identify.apply(event.getCurrentItem())
                    .ifPresent(item -> holder.screen.clickPlayerItem(item, ClickKinds.of(event.getClick())));
        }
        draw(holder);
    }

    /**
     * @param event clic, apres les autres plugins
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void keepClickCancelled(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof EditorHolder) {
            event.setCancelled(true);
        }
    }

    /**
     * @param event glisser
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof EditorHolder) {
            event.setCancelled(true);
        }
    }

    /**
     * @param event fermeture d'un inventaire
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof EditorHolder holder && open.remove(holder.owner, holder)) {
            holder.onClose.run();
        }
    }

    /**
     * @param event deconnexion
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        prompts.remove(event.getPlayer().getUniqueId());
        EditorHolder holder = open.remove(event.getPlayer().getUniqueId());
        if (holder != null) {
            holder.onClose.run();
        }
    }

    @Override
    public void close() {
        HandlerList.unregisterAll(this);
        for (EditorHolder holder : List.copyOf(open.values())) {
            open.remove(holder.owner, holder);
            Player player = players.apply(holder.owner);
            if (player != null && accessibleHere.test(holder.owner)) {
                player.closeInventory();
            }
        }
    }
}
