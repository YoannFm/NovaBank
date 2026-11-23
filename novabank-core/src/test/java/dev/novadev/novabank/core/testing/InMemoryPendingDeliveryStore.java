package dev.novadev.novabank.core.testing;

import dev.novadev.novabank.api.OperationId;
import dev.novadev.novabank.core.delivery.PendingDelivery;
import dev.novadev.novabank.core.delivery.PendingDeliveryStore;
import dev.novadev.novabank.core.storage.StoredDelivery;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * {@link PendingDeliveryStore} en memoire, idempotent par operation d'origine. Une
 * livraison entierement remise est conservee (quantite remise = quantite due).
 */
public final class InMemoryPendingDeliveryStore implements PendingDeliveryStore {

    private final Map<OperationId, Entry> deliveries = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    @Override
    public void add(PendingDelivery delivery) {
        deliveries.putIfAbsent(delivery.operationId(), new Entry(delivery, sequence.incrementAndGet(), Instant.now()));
    }

    @Override
    public Optional<PendingDelivery> find(OperationId operationId) {
        Entry entry = deliveries.get(operationId);
        return entry == null ? Optional.empty() : entry.remaining();
    }

    @Override
    public List<PendingDelivery> pendingFor(UUID playerId) {
        return deliveries.values().stream()
                .filter(entry -> entry.delivery.playerId().equals(playerId))
                .sorted((a, b) -> Long.compare(a.order, b.order))
                .flatMap(entry -> entry.remaining().stream())
                .toList();
    }

    @Override
    public boolean markDelivered(OperationId operationId, long quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantite remise non positive : " + quantity);
        }
        Entry entry = deliveries.get(operationId);
        if (entry == null) {
            return false;
        }
        synchronized (entry) {
            if (entry.delivery.quantity() - entry.delivered < quantity) {
                return false;
            }
            entry.delivered += quantity;
            return true;
        }
    }

    /**
     * @return l'etat complet de chaque livraison, par ordre d'enregistrement (export du contenu)
     */
    public List<StoredDelivery> stored() {
        return deliveries.values().stream()
                .sorted((a, b) -> Long.compare(a.order, b.order))
                .map(Entry::stored)
                .toList();
    }

    /**
     * Enregistre une livraison telle quelle, apres les autres (import du contenu).
     *
     * @param stored livraison et quantite deja remise
     */
    public void restore(StoredDelivery stored) {
        Entry entry = new Entry(stored.delivery(), sequence.incrementAndGet(), stored.createdAt());
        entry.delivered = stored.deliveredQuantity();
        deliveries.put(stored.delivery().operationId(), entry);
    }

    /**
     * @return nombre total de livraisons connues, remises ou non
     */
    public int size() {
        return deliveries.size();
    }

    private static final class Entry {

        private final PendingDelivery delivery;
        private final long order;
        private final Instant createdAt;
        private long delivered;

        Entry(PendingDelivery delivery, long order, Instant createdAt) {
            this.delivery = delivery;
            this.order = order;
            this.createdAt = createdAt;
        }

        synchronized StoredDelivery stored() {
            return new StoredDelivery(delivery, delivered, createdAt);
        }

        synchronized Optional<PendingDelivery> remaining() {
            long due = delivery.quantity() - delivered;
            return due <= 0 ? Optional.empty() : Optional.of(new PendingDelivery(delivery.operationId(),
                    delivery.playerId(), delivery.itemKey(), due));
        }
    }
}
