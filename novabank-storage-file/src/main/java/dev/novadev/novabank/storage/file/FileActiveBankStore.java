package dev.novadev.novabank.storage.file;

import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.core.selection.ActiveBankStore;
import dev.novadev.novabank.core.storage.ActiveBankChoice;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link ActiveBankStore} fichier.
 */
final class FileActiveBankStore implements ActiveBankStore {

    private final FileStore store;

    FileActiveBankStore(FileStore store) {
        this.store = store;
    }

    @Override
    public Optional<EntityRef> find(UUID playerId) {
        return store.read(state -> Optional.ofNullable(state.activeBank(playerId)));
    }

    @Override
    public void save(UUID playerId, EntityRef entity) {
        ActiveBankChoice choice = new ActiveBankChoice(playerId, entity);
        store.write(state -> {
            store.commit(Commit.of(choice));
            return null;
        });
    }
}
