package dev.novadev.novabank.storage.file;

import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.api.OperationId;
import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.operation.OperationType;
import dev.novadev.novabank.core.bank.Bank;
import dev.novadev.novabank.core.journal.OperationRecord;
import dev.novadev.novabank.core.journal.OperationState;
import dev.novadev.novabank.core.storage.ActiveBankChoice;
import dev.novadev.novabank.core.storage.StoredDelivery;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Etat en memoire d'un stockage fichier et ses index. Non synchronise : toujours utilise sous le
 * verrou de {@link FileStore}. {@link #apply(Commit)} est la seule mutation, commune au
 * fonctionnement normal et au rejeu du journal ; elle est idempotente (etats complets).
 */
final class FileState {

    /** Ordre du journal : horodatage puis texte de l'identifiant (contrat du depot). */
    private static final Comparator<Key> ORDER = Comparator.comparing(Key::timestamp).thenComparing(Key::text);

    private final Map<BankId, Bank> banks = new HashMap<>();
    private final Map<OperationId, OperationRecord> operations = new HashMap<>();
    private final Map<BankId, NavigableSet<Key>> history = new HashMap<>();
    private final NavigableSet<Key> reserved = new TreeSet<>(ORDER);
    private final Map<OperationId, StoredDelivery> deliveries = new LinkedHashMap<>();
    private final Map<UUID, Set<OperationId>> deliveriesByPlayer = new HashMap<>();
    private final Map<UUID, EntityRef> activeBanks = new HashMap<>();

    /** Entree du journal dans un index ordonne. */
    private record Key(Instant timestamp, String text, OperationId id) {

        static Key of(OperationRecord record) {
            return new Key(record.timestamp(), record.operationId().toString(), record.operationId());
        }
    }

    /**
     * Applique des etats complets.
     *
     * @param commit elements a enregistrer
     */
    void apply(Commit commit) {
        commit.banks().forEach(bank -> banks.put(bank.id(), bank));
        for (OperationRecord record : commit.operations()) {
            OperationRecord previous = operations.put(record.operationId(), record);
            if (previous != null) {
                // Index de l'etat precedent retire : une entree ne figure qu'une fois, meme si sa
                // banque ou son horodatage different (rejeu, import).
                Key old = Key.of(previous);
                NavigableSet<Key> previousHistory = history.get(previous.bankId());
                if (previousHistory != null) {
                    previousHistory.remove(old);
                }
                reserved.remove(old);
            }
            Key key = Key.of(record);
            history.computeIfAbsent(record.bankId(), id -> new TreeSet<>(ORDER)).add(key);
            if (record.type() == OperationType.WITHDRAW && record.state() == OperationState.RESERVED) {
                reserved.add(key);
            } else {
                reserved.remove(key);
            }
        }
        for (StoredDelivery delivery : commit.deliveries()) {
            OperationId id = delivery.delivery().operationId();
            deliveries.put(id, delivery);
            deliveriesByPlayer.computeIfAbsent(delivery.delivery().playerId(), player -> new LinkedHashSet<>()).add(id);
        }
        commit.activeBanks().forEach(choice -> activeBanks.put(choice.playerId(), choice.entity()));
    }

    Bank bank(BankId bankId) {
        return banks.get(bankId);
    }

    OperationRecord operation(OperationId operationId) {
        return operations.get(operationId);
    }

    StoredDelivery delivery(OperationId operationId) {
        return deliveries.get(operationId);
    }

    EntityRef activeBank(UUID playerId) {
        return activeBanks.get(playerId);
    }

    /**
     * @return les retraits reserves, par horodatage puis identifiant
     */
    List<OperationRecord> reservedWithdrawals() {
        List<OperationRecord> records = new ArrayList<>(reserved.size());
        reserved.forEach(key -> records.add(operations.get(key.id())));
        return records;
    }

    /**
     * @return une page du journal d'une banque, du plus recent au plus ancien
     */
    List<OperationRecord> history(BankId bankId, int offset, int limit) {
        NavigableSet<Key> keys = history.get(bankId);
        if (keys == null) {
            return List.of();
        }
        List<OperationRecord> page = new ArrayList<>(Math.min(limit, keys.size()));
        int skipped = 0;
        for (Key key : keys.descendingSet()) {
            if (skipped++ < offset) {
                continue;
            }
            if (page.size() == limit) {
                break;
            }
            page.add(operations.get(key.id()));
        }
        return page;
    }

    /**
     * @return les livraisons du joueur, par ordre d'enregistrement
     */
    List<StoredDelivery> deliveriesOf(UUID playerId) {
        Set<OperationId> ids = deliveriesByPlayer.getOrDefault(playerId, Set.of());
        List<StoredDelivery> list = new ArrayList<>(ids.size());
        ids.forEach(id -> list.add(deliveries.get(id)));
        return list;
    }

    boolean isEmpty() {
        return banks.isEmpty() && operations.isEmpty() && deliveries.isEmpty() && activeBanks.isEmpty();
    }

    /**
     * @return les banques, par identifiant
     */
    List<Bank> banks() {
        List<Bank> all = new ArrayList<>(banks.values());
        all.sort(Comparator.comparing(bank -> bank.id().toString()));
        return all;
    }

    /**
     * @return le journal, par horodatage puis identifiant
     */
    List<OperationRecord> operations() {
        List<OperationRecord> all = new ArrayList<>(operations.values());
        all.sort(Comparator.comparing(OperationRecord::timestamp)
                .thenComparing(record -> record.operationId().toString()));
        return all;
    }

    /**
     * @return les livraisons, par ordre d'enregistrement
     */
    List<StoredDelivery> deliveries() {
        return new ArrayList<>(deliveries.values());
    }

    /**
     * @return les banques actives, par joueur
     */
    List<ActiveBankChoice> activeBanks() {
        List<ActiveBankChoice> choices = new ArrayList<>(activeBanks.size());
        activeBanks.forEach((player, entity) -> choices.add(new ActiveBankChoice(player, entity)));
        choices.sort(Comparator.comparing(choice -> choice.playerId().toString()));
        return choices;
    }

    /**
     * @return l'etat complet (instantane)
     */
    Commit snapshot() {
        return new Commit(banks(), operations(), deliveries(), activeBanks());
    }
}
