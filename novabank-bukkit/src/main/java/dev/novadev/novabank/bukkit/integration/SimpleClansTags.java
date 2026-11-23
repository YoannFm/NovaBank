package dev.novadev.novabank.bukkit.integration;

import dev.novadev.novabank.api.entity.EntityRef;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BiConsumer;
import org.bukkit.ChatColor;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

/**
 * La banque d'un clan SimpleClans suit son tag (identifiant de la banque) : quand un clan change de tag, tout le stock
 * de l'ancienne banque passe dans celle du nouveau tag. Ecoute {@code TagChangeEvent} par reflexion ; signatures
 * VERIFIEES avec SimpleClans 2.19.2 (javap) : {@code getClan()}, {@code getNewTag()}, {@code isCancelled()},
 * {@code Clan.getTag()} (encore l'ancien tag pendant l'evenement).
 */
public final class SimpleClansTags implements Listener, AutoCloseable {

    private static final String EVENT = "net.sacredlabyrinth.phaed.simpleclans.events.TagChangeEvent";
    private static final String CLAN = "clan";

    private final BiConsumer<EntityRef, EntityRef> move;

    /** @param move deplace tout le stock d'une banque vers une autre (transfert journalise) */
    public SimpleClansTags(BiConsumer<EntityRef, EntityRef> move) {
        this.move = move;
    }

    /**
     * @param simpleClans plugin SimpleClans
     * @param plugins     gestionnaire de plugins
     * @param owner       plugin proprietaire de l'ecoute
     * @return vrai si l'evenement est reconnu
     */
    public boolean hook(Plugin simpleClans, PluginManager plugins, Plugin owner) {
        Optional<Class<?>> type = Reflect.type(simpleClans.getClass().getClassLoader(), EVENT)
                .filter(Event.class::isAssignableFrom);
        if (type.isEmpty()) {
            return false;
        }
        plugins.registerEvent(type.get().asSubclass(Event.class), this, EventPriority.MONITOR, (listener, event) -> {
            if (type.get().isInstance(event)) {
                onTagChange(event);
            }
        }, owner, true);
        return true;
    }

    private void onTagChange(Object event) {
        Optional<String> before = Reflect.call(event, "getClan").flatMap(clan -> Reflect.call(clan, "getTag"))
                .map(tag -> id(tag.toString()));
        Optional<String> after = Reflect.call(event, "getNewTag").map(tag -> id(tag.toString()));
        if (before.isPresent() && after.isPresent() && !after.get().isEmpty() && !before.get().equals(after.get())) {
            move.accept(new EntityRef(CLAN, before.get()), new EntityRef(CLAN, after.get()));
        }
    }

    /** Identifiant de banque d'un tag, comme {@link SimpleClansOwnership} : sans couleurs, en minuscules. */
    static String id(String tag) {
        String plain = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', tag));
        return plain == null ? "" : plain.toLowerCase(Locale.ROOT);
    }

    @Override
    public void close() {
        HandlerList.unregisterAll(this);
    }
}
