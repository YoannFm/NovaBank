package dev.novadev.novabank.core.application;

import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.api.operation.OperationType;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Operations des joueurs : depot, retrait, consultation, choix de la banque active et
 * remise des livraisons en attente. Java pur, sans dependance serveur.
 *
 * <h2>Anti-duplication</h2>
 * Toute ecriture en base passe par l'executeur d'entrees/sorties, toute manipulation
 * d'inventaire par le thread du joueur. Les items quittent l'inventaire AVANT la
 * validation d'un depot, et n'y entrent qu'APRES la reservation d'un retrait : la base
 * reste la source de verite.
 *
 * <p>Un crash du serveur ramene l'inventaire du joueur a sa derniere sauvegarde. Entre la
 * validation en base et cette sauvegarde, inventaire et base divergent : un depot valide
 * dont l'inventaire n'est pas sauve rendrait les items au joueur (duplication). C'est
 * pourquoi les donnees du joueur sont sauvegardees juste APRES chaque validation (option
 * {@code security.save-player-data}), jamais avant : la fenetre de duplication est
 * ramenee au delai entre la validation et la sauvegarde (un tick). Risques residuels
 * documentes : sauvegarde automatique du serveur entre le retrait des items et la
 * validation suivie d'un crash (perte du depot en cours) ; base indisponible au moment de
 * confirmer un retrait deja remis (voir {@link WithdrawalFlow}).
 *
 * <p>Un seul flux par joueur a la fois ({@link PlayerOperationGate}), un delai minimal et
 * une quantite maximale par operation. Droits (permission et role) verifies cote serveur
 * a chaque operation. Les resultats sont livres sur le thread du joueur.
 *
 * <p>Arret : les flux qui deplacent des items sont suivis par {@link FlowDrain} ; pendant
 * l'arret, un nouveau depot ou retrait est refuse ({@link TransferStatus#SHUTTING_DOWN})
 * et la remise des livraisons n'est pas lancee ; de meme pendant une maintenance
 * ({@link TransferStatus#MAINTENANCE}).
 */
public final class BankService {

    private final OperationPorts ports;

    /**
     * @param ports collaborateurs
     */
    public BankService(OperationPorts ports) {
        this.ports = Objects.requireNonNull(ports, "ports");
    }

    /**
     * Depose un ou plusieurs items dans la banque active du joueur.
     *
     * @param playerId joueur connecte
     * @param choice   items vises
     * @param amount   quantite par item
     * @param done     resultats, un par item (ou un seul refus global)
     */
    public void deposit(UUID playerId, ItemChoice choice, Amount amount, Consumer<List<TransferResult>> done) {
        Objects.requireNonNull(choice, "choice");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(done, "done");
        TransferRules rules = ports.rules().get();
        enter(playerId, OperationType.DEPOSIT, rules, refusal -> deliver(playerId, () -> done.accept(List.of(refusal))),
                () -> new DepositFlow(ports, playerId, choice, amount, rules, done, release(playerId)).start());
    }

    /**
     * Retire un item de la banque active du joueur.
     *
     * @param playerId joueur connecte
     * @param item     item
     * @param amount   quantite
     * @param done     resultat
     */
    public void withdraw(UUID playerId, ItemKey item, Amount amount, Consumer<TransferResult> done) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(done, "done");
        TransferRules rules = ports.rules().get();
        enter(playerId, OperationType.WITHDRAW, rules, refusal -> deliver(playerId, () -> done.accept(refusal)),
                () -> new WithdrawalFlow(ports, playerId, item, amount, rules, done, release(playerId)).start());
    }

    /**
     * Consulte la banque active du joueur.
     *
     * @param playerId joueur connecte
     * @param done     resultat
     */
    public void info(UUID playerId, Consumer<BankView> done) {
        new InfoFlow(ports, playerId, ports.rules().get(), Objects.requireNonNull(done, "done")).start();
    }

    /**
     * Choisit la banque active du joueur parmi ses entites du type demande.
     *
     * @param playerId   joueur connecte
     * @param entityType type d'entite
     * @param done       resultat
     */
    public void select(UUID playerId, String entityType, Consumer<SelectionResult> done) {
        select(playerId, entityType, "", done);
    }

    /**
     * Rend active la banque d'un type pour une commande d'hote : l'entite preferee par l'hote (lieu du joueur,
     * commande tapee), sinon celle deja choisie, sinon la premiere.
     *
     * @param playerId   joueur
     * @param entityType type impose
     * @param command    commande tapee ({@code ob bank}), vide sinon
     * @param done       resultat
     */
    public void select(UUID playerId, String entityType, String command, Consumer<SelectionResult> done) {
        new SelectFlow(ports, playerId, Objects.requireNonNull(entityType, "entityType"),
                Objects.requireNonNull(command, "command"), ports.rules().get(),
                Objects.requireNonNull(done, "done")).start();
    }

    /**
     * Remet au joueur ses livraisons en attente. Sans effet si une operation du joueur est
     * deja en cours ou si NovaBank s'arrete (la remise sera faite a la connexion suivante).
     *
     * @param playerId joueur connecte
     * @param done     resultat (non appele si la remise n'est pas lancee)
     */
    public void deliverPending(UUID playerId, Consumer<DeliveryReport> done) {
        Objects.requireNonNull(done, "done");
        if (!ports.drain().tryStart()) {
            return;
        }
        if (!ports.gate().tryLock(playerId)) {
            ports.drain().finished();
            return;
        }
        new DeliveryFlow(ports, playerId, ports.rules().get().savePlayerData(), done, release(playerId)).start();
    }

    private void enter(UUID playerId, OperationType type, TransferRules rules, Consumer<TransferResult> refused,
                       Runnable flow) {
        Objects.requireNonNull(playerId, "playerId");
        if (!ports.drain().tryStart()) {
            refused.accept(TransferResult.refused(type, ports.drain().isShuttingDown()
                    ? TransferStatus.SHUTTING_DOWN : TransferStatus.MAINTENANCE));
            return;
        }
        PlayerOperationGate.Entry entry = ports.gate().tryEnter(playerId, rules.cooldown());
        if (entry.status() != PlayerOperationGate.Status.ACCEPTED) {
            ports.drain().finished();
        }
        switch (entry.status()) {
            case ACCEPTED -> flow.run();
            case BUSY -> refused.accept(TransferResult.refused(type, TransferStatus.BUSY));
            case COOLDOWN -> refused.accept(TransferResult.cooldown(type, entry.remaining()));
        }
    }

    /**
     * @param playerId joueur
     * @return fin d'un flux : verrou du joueur libere, flux retire du suivi d'arret
     */
    private Runnable release(UUID playerId) {
        return () -> {
            ports.gate().leave(playerId);
            ports.drain().finished();
        };
    }

    private void deliver(UUID playerId, Runnable callback) {
        ports.scheduler().runForPlayer(playerId, callback, () -> { });
    }
}
