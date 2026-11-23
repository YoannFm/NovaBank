package dev.novadev.novabank.core.testing;

import dev.novadev.novabank.core.bank.Bank;
import dev.novadev.novabank.core.journal.OperationRecord;
import dev.novadev.novabank.core.storage.ActiveBankChoice;
import dev.novadev.novabank.core.storage.StorageBackend;
import dev.novadev.novabank.core.storage.StorageContent;
import dev.novadev.novabank.core.storage.StoredDelivery;
import java.util.List;
import java.util.function.Consumer;

/**
 * {@link StorageBackend} en memoire : les trois depots en memoire et leur contenu complet.
 */
public final class InMemoryStorageBackend implements StorageBackend {

    private final InMemoryBankRepository banks = new InMemoryBankRepository();
    private final InMemoryPendingDeliveryStore deliveries = new InMemoryPendingDeliveryStore();
    private final InMemoryActiveBankStore activeBanks = new InMemoryActiveBankStore();
    private final StorageContent content = new Content();

    @Override
    public InMemoryBankRepository banks() {
        return banks;
    }

    @Override
    public InMemoryPendingDeliveryStore deliveries() {
        return deliveries;
    }

    @Override
    public InMemoryActiveBankStore activeBanks() {
        return activeBanks;
    }

    @Override
    public StorageContent content() {
        return content;
    }

    @Override
    public int schemaVersion() {
        return 1;
    }

    @Override
    public int checkSchemaVersion() {
        return 1;
    }

    @Override
    public void close() {
        // Rien a liberer.
    }

    private final class Content implements StorageContent {

        @Override
        public boolean isEmpty() {
            return banks.banks().isEmpty() && banks.journal().isEmpty() && deliveries.size() == 0
                    && activeBanks.choices().isEmpty();
        }

        @Override
        public void exportBanks(Consumer<Bank> sink) {
            banks.banks().forEach(sink);
        }

        @Override
        public void exportOperations(Consumer<OperationRecord> sink) {
            banks.journal().forEach(sink);
        }

        @Override
        public void exportDeliveries(Consumer<StoredDelivery> sink) {
            deliveries.stored().forEach(sink);
        }

        @Override
        public void exportActiveBanks(Consumer<ActiveBankChoice> sink) {
            activeBanks.choices().forEach((player, entity) -> sink.accept(new ActiveBankChoice(player, entity)));
        }

        @Override
        public void importBanks(List<Bank> imported) {
            imported.forEach(banks::restore);
        }

        @Override
        public void importOperations(List<OperationRecord> records) {
            records.forEach(banks::restore);
        }

        @Override
        public void importDeliveries(List<StoredDelivery> imported) {
            imported.forEach(deliveries::restore);
        }

        @Override
        public void importActiveBanks(List<ActiveBankChoice> choices) {
            choices.forEach(choice -> activeBanks.save(choice.playerId(), choice.entity()));
        }
    }
}
