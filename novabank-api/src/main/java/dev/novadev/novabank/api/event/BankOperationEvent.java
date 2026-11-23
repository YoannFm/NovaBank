package dev.novadev.novabank.api.event;

import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.api.OperationId;
import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.api.operation.OperationType;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Une operation vient d'etre ENREGISTREE par le stockage (depot, retrait reserve puis solde, reinitialisation),
 * quelle que soit son origine : commande, GUI ou {@code NovaBankApi}. L'evenement n'est ni annulable ni
 * "avant" : il decrit un fait accompli (l'annulation d'un depot ou d'un retrait n'est pas sure une fois les items
 * deplaces, CDC 13.2).
 *
 * <p><b>Evenement asynchrone</b> : appele depuis le thread d'entrees/sorties de NovaBank, jamais depuis le thread
 * principal. Les ecouteurs doivent etre sures entre threads et rapides, et planifier eux-memes toute action sur le
 * monde ou les joueurs. Un retrait produit deux evenements : la reservation puis son solde (etat {@code COMMITTED}
 * ou {@code CANCELLED}).
 */
public final class BankOperationEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final OperationId operationId;
    private final BankId bankId;
    private final EntityRef owner;
    private final OperationType type;
    private final String state;
    private final ItemKey item;
    private final long quantity;
    private final BigDecimal valueDelta;
    private final long bankVersion;
    private final UUID actor;

    /**
     * @param operationId identifiant de l'operation
     * @param bankId      banque modifiee
     * @param owner       entite proprietaire de la banque
     * @param type        nature de l'operation
     * @param state       etat de l'entree de journal ({@code COMMITTED}, {@code RESERVED}, {@code CANCELLED}...)
     * @param item        item concerne, {@code null} pour une reinitialisation
     * @param quantity    unites deplacees (stock total pour une reinitialisation)
     * @param valueDelta  variation de valeur de la banque
     * @param bankVersion version de la banque apres l'operation
     */
    public BankOperationEvent(OperationId operationId, BankId bankId, EntityRef owner, OperationType type, String state,
                              ItemKey item, long quantity, BigDecimal valueDelta, long bankVersion) {
        this(operationId, bankId, owner, type, state, item, quantity, valueDelta, bankVersion, null);
    }

    /**
     * @param operationId identifiant de l'operation
     * @param bankId      banque modifiee
     * @param owner       entite proprietaire de la banque
     * @param type        nature de l'operation
     * @param state       etat de l'entree de journal
     * @param item        item concerne, {@code null} pour une reinitialisation
     * @param quantity    unites deplacees (stock total pour une reinitialisation)
     * @param valueDelta  variation de valeur de la banque
     * @param bankVersion version de la banque apres l'operation
     * @param actor       joueur qui a lance l'operation sur ce serveur, {@code null} s'il n'est pas connu
     * @since 1.0.0
     */
    public BankOperationEvent(OperationId operationId, BankId bankId, EntityRef owner, OperationType type, String state,
                              ItemKey item, long quantity, BigDecimal valueDelta, long bankVersion, UUID actor) {
        super(true);
        this.operationId = Objects.requireNonNull(operationId, "operationId");
        this.bankId = Objects.requireNonNull(bankId, "bankId");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.type = Objects.requireNonNull(type, "type");
        this.state = Objects.requireNonNull(state, "state");
        this.item = item;
        this.quantity = quantity;
        this.valueDelta = Objects.requireNonNull(valueDelta, "valueDelta");
        this.bankVersion = bankVersion;
        this.actor = actor;
    }

    /**
     * @return le joueur qui a lance l'operation, vide si elle vient de l'API, d'un autre serveur ou de
     *         l'administration
     * @since 1.0.0
     */
    public Optional<UUID> actor() {
        return Optional.ofNullable(actor);
    }

    /** @return identifiant de l'operation */
    public OperationId operationId() {
        return operationId;
    }

    /** @return banque modifiee */
    public BankId bankId() {
        return bankId;
    }

    /** @return entite proprietaire */
    public EntityRef owner() {
        return owner;
    }

    /** @return nature de l'operation */
    public OperationType type() {
        return type;
    }

    /** @return etat de l'entree de journal */
    public String state() {
        return state;
    }

    /** @return item concerne, vide pour une reinitialisation */
    public Optional<ItemKey> item() {
        return Optional.ofNullable(item);
    }

    /** @return unites deplacees */
    public long quantity() {
        return quantity;
    }

    /** @return variation de valeur (negative pour une sortie) */
    public BigDecimal valueDelta() {
        return valueDelta;
    }

    /** @return version de la banque apres l'operation */
    public long bankVersion() {
        return bankVersion;
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
