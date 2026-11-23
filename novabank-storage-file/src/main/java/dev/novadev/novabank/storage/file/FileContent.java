package dev.novadev.novabank.storage.file;

import dev.novadev.novabank.core.bank.Bank;
import dev.novadev.novabank.core.journal.OperationRecord;
import dev.novadev.novabank.core.repository.StorageException;
import dev.novadev.novabank.core.storage.ActiveBankChoice;
import dev.novadev.novabank.core.storage.StorageContent;
import dev.novadev.novabank.core.storage.StoredDelivery;
import dev.novadev.novabank.core.text.ConsoleText;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * {@link StorageContent} fichier : la lecture copie l'etat sous verrou partage puis le transmet
 * hors verrou ; chaque lot importe est une ligne de journal synchronisee.
 */
final class FileContent implements StorageContent {

    private final FileStore store;

    FileContent(FileStore store) {
        this.store = store;
    }

    @Override
    public boolean isEmpty() {
        return store.read(FileState::isEmpty);
    }

    @Override
    public void exportBanks(Consumer<Bank> sink) {
        store.read(FileState::banks).forEach(sink);
    }

    @Override
    public void exportOperations(Consumer<OperationRecord> sink) {
        store.read(FileState::operations).forEach(sink);
    }

    @Override
    public void exportDeliveries(Consumer<StoredDelivery> sink) {
        store.read(FileState::deliveries).forEach(sink);
    }

    @Override
    public void exportActiveBanks(Consumer<ActiveBankChoice> sink) {
        store.read(FileState::activeBanks).forEach(sink);
    }

    @Override
    public void importBanks(List<Bank> banks) {
        importAll(banks, bank -> "banque " + bank.id(), (state, bank) -> state.bank(bank.id()) != null,
                list -> new Commit(list, List.of(), List.of(), List.of()));
    }

    @Override
    public void importOperations(List<OperationRecord> records) {
        importAll(records, record -> "operation " + record.operationId(),
                (state, record) -> state.operation(record.operationId()) != null,
                list -> new Commit(List.of(), list, List.of(), List.of()));
    }

    @Override
    public void importDeliveries(List<StoredDelivery> deliveries) {
        importAll(deliveries, stored -> "livraison " + stored.delivery().operationId(),
                (state, stored) -> state.delivery(stored.delivery().operationId()) != null,
                list -> new Commit(List.of(), List.of(), list, List.of()));
    }

    @Override
    public void importActiveBanks(List<ActiveBankChoice> choices) {
        importAll(choices, choice -> "banque active de " + choice.playerId(),
                (state, choice) -> state.activeBank(choice.playerId()) != null,
                list -> new Commit(List.of(), List.of(), List.of(), list));
    }

    private <T> void importAll(List<T> elements, Function<T, String> name, Present<T> present,
                               Function<List<T>, Commit> commit) {
        if (elements.isEmpty()) {
            return;
        }
        store.write(state -> {
            Predicate<T> exists = element -> present.test(state, element);
            elements.stream().filter(exists).findFirst().ifPresent(element -> {
                throw new StorageException(ConsoleText.tr("Import refuse : {0} deja presente", name.apply(element)));
            });
            store.commit(commit.apply(elements));
            return null;
        });
    }

    /** Presence d'un element dans l'etat. */
    @FunctionalInterface
    private interface Present<T> {
        boolean test(FileState state, T element);
    }
}
