package dev.novadev.novabank.api.event;

import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.api.entity.EntityRef;
import java.util.Objects;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Une banque vient d'etre creee par CE serveur (premiere operation d'une entite). Evenement asynchrone (thread
 * d'entrees/sorties), non annulable. NovaBank ne supprime jamais une banque : il n'existe pas d'evenement de
 * suppression (CDC 3.3, aucune suppression sans regle explicite).
 */
public final class BankCreatedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final BankId bankId;
    private final EntityRef owner;

    /**
     * @param bankId identifiant de la banque
     * @param owner  entite proprietaire
     */
    public BankCreatedEvent(BankId bankId, EntityRef owner) {
        super(true);
        this.bankId = Objects.requireNonNull(bankId, "bankId");
        this.owner = Objects.requireNonNull(owner, "owner");
    }

    /** @return identifiant de la banque */
    public BankId bankId() {
        return bankId;
    }

    /** @return entite proprietaire */
    public EntityRef owner() {
        return owner;
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
