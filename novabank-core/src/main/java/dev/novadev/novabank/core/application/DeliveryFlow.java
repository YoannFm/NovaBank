package dev.novadev.novabank.core.application;

import dev.novadev.novabank.core.delivery.PendingDelivery;
import dev.novadev.novabank.core.text.ConsoleText;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Remise des livraisons en attente d'un joueur (a sa connexion).
 *
 * <ol>
 *   <li>IO : lecture des livraisons dues ;</li>
 *   <li>thread joueur : remise, dans la limite de la place ;</li>
 *   <li>IO : {@code markDelivered} des quantites remises (le reste reste du) ;</li>
 *   <li>thread joueur : sauvegarde des donnees du joueur.</li>
 * </ol>
 *
 * <p>Si l'enregistrement d'une remise echoue apres toutes les tentatives, l'echec est
 * journalise en SEVERE : la livraison reste due et serait remise une seconde fois a la
 * connexion suivante (risque residuel, a verifier manuellement). Les remises enregistrees
 * sont protegees par la sauvegarde des donnees du joueur.
 */
final class DeliveryFlow extends Flow<DeliveryReport> {

    private final boolean savePlayerData;
    private final List<Given> given = new ArrayList<>();
    private long remaining;

    DeliveryFlow(OperationPorts ports, UUID playerId, boolean savePlayerData, Consumer<DeliveryReport> done,
                 Runnable release) {
        super(ports, playerId, done, release);
        this.savePlayerData = savePlayerData;
    }

    void start() {
        onIo(this::load);
    }

    private void load() {
        List<PendingDelivery> pending = ports.deliveries().pendingFor(playerId);
        if (pending.isEmpty()) {
            finish(DeliveryReport.NOTHING);
            return;
        }
        onPlayer(() -> give(pending), () -> finish(DeliveryReport.NOTHING));
    }

    private void give(List<PendingDelivery> pending) {
        for (PendingDelivery delivery : pending) {
            long leftover = ports.inventory().give(playerId, delivery.itemKey(), delivery.quantity());
            long delivered = delivery.quantity() - Math.max(0L, Math.min(leftover, delivery.quantity()));
            remaining += delivery.quantity() - delivered;
            if (delivered > 0) {
                given.add(new Given(delivery, delivered));
            }
        }
        if (given.isEmpty()) {
            finish(new DeliveryReport(0L, remaining));
            return;
        }
        onIo(this::markDelivered);
    }

    private void markDelivered() {
        long recorded = 0L;
        for (Given item : given) {
            boolean marked = ports.retry().call("remise de la livraison " + item.delivery.operationId(),
                    () -> ports.deliveries().markDelivered(item.delivery.operationId(), item.quantity)).orElse(false);
            if (marked) {
                recorded += item.quantity;
            } else {
                LOGGER.log(System.Logger.Level.ERROR,
                        ConsoleText.tr("Remise de la livraison {0} ({1} x {2} au joueur {3}) non enregistree : risque de seconde remise, verifier manuellement.",
                                item.delivery.operationId(), item.quantity, item.delivery.itemKey(), playerId));
            }
        }
        DeliveryReport report = new DeliveryReport(given.stream().mapToLong(item -> item.quantity).sum(), remaining);
        if (recorded == 0L || !savePlayerData) {
            finish(report);
            return;
        }
        onPlayer(() -> {
            ports.playerData().save(playerId);
            finish(report);
        }, () -> finish(report));
    }

    @Override
    void failed(RuntimeException failure) {
        LOGGER.log(System.Logger.Level.ERROR, ConsoleText.tr("Remise des livraisons du joueur {0} interrompue : {1}",
                playerId, failure), failure);
        finish(DeliveryReport.NOTHING);
    }

    private record Given(PendingDelivery delivery, long quantity) {
    }
}
