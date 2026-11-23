package dev.novadev.novabank.api.event;

import java.util.Objects;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Un composant de NovaBank (stockage, synchronisation, integration de territoire, fournisseur d'items) est
 * disponible ou ne l'est pas, constate au demarrage. Evenement synchrone, appele depuis le thread principal.
 */
public final class ComponentStateEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    /** Famille du composant. */
    public enum Kind {
        /** Backend de stockage. */
        STORAGE,
        /** Synchronisation multi-serveur (Redis). */
        SYNC,
        /** Integration d'iles, de claims ou de territoires. */
        INTEGRATION,
        /** Fournisseur d'items custom. */
        ITEM_PROVIDER
    }

    private final Kind kind;
    private final String name;
    private final boolean available;
    private final String detail;

    /**
     * @param kind      famille
     * @param name      nom du composant (par exemple {@code BentoBox}, {@code MARIADB})
     * @param available {@code true} s'il est utilisable
     * @param detail    precision lisible, jamais de secret
     */
    public ComponentStateEvent(Kind kind, String name, boolean available, String detail) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.name = Objects.requireNonNull(name, "name");
        this.available = available;
        this.detail = Objects.requireNonNull(detail, "detail");
    }

    /** @return famille */
    public Kind kind() {
        return kind;
    }

    /** @return nom du composant */
    public String name() {
        return name;
    }

    /** @return {@code true} s'il est utilisable */
    public boolean available() {
        return available;
    }

    /** @return precision lisible */
    public String detail() {
        return detail;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    /** @return liste des ecouteurs (exigee par Bukkit) */
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
