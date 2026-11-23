package dev.novadev.novabank.storage.file;

import dev.novadev.novabank.api.OperationId;
import dev.novadev.novabank.core.delivery.PendingDelivery;
import dev.novadev.novabank.core.delivery.PendingDeliveryStore;
import dev.novadev.novabank.core.storage.StoredDelivery;
import dev.novadev.novabank.core.text.ConsoleText;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link PendingDeliveryStore} fichier, idempotent par operation d'origine ; une livraison
 * entierement remise est conservee (quantite remise = quantite due).
 */
final class FileDeliveryStore implements PendingDeliveryStore {

    private final FileStore store;
    private final Clock clock;

    FileDeliveryStore(FileStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @Override
    public void add(PendingDelivery delivery) {
        store.write(state -> {
            if (state.delivery(delivery.operationId()) == null) {
                store.commit(Commit.of(new StoredDelivery(delivery, 0L, clock.instant())));
            }
            return null;
        });
    }

    @Override
    public Optional<PendingDelivery> find(OperationId operationId) {
        return store.read(state -> remaining(state.delivery(operationId)));
    }

    @Override
    public List<PendingDelivery> pendingFor(UUID playerId) {
        return store.read(state -> state.deliveriesOf(playerId).stream()
                .flatMap(stored -> remaining(stored).stream())
                .toList());
    }

    @Override
    public boolean markDelivered(OperationId operationId, long quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException(ConsoleText.tr("Quantite remise non positive : {0}", quantity));
        }
        return store.write(state -> {
            StoredDelivery stored = state.delivery(operationId);
            if (stored == null || stored.remainingQuantity() < quantity) {
                return false;
            }
            store.commit(Commit.of(new StoredDelivery(stored.delivery(), stored.deliveredQuantity() + quantity,
                    stored.createdAt())));
            return true;
        });
    }

    private static Optional<PendingDelivery> remaining(StoredDelivery stored) {
        if (stored == null || stored.remainingQuantity() == 0) {
            return Optional.empty();
        }
        PendingDelivery delivery = stored.delivery();
        return Optional.of(new PendingDelivery(delivery.operationId(), delivery.playerId(), delivery.itemKey(),
                stored.remainingQuantity()));
    }
}
