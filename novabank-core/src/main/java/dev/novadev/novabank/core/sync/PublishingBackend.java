package dev.novadev.novabank.core.sync;

import dev.novadev.novabank.core.delivery.PendingDeliveryStore;
import dev.novadev.novabank.core.repository.BankRepository;
import dev.novadev.novabank.core.selection.ActiveBankStore;
import dev.novadev.novabank.core.storage.StorageBackend;
import dev.novadev.novabank.core.storage.StorageContent;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

/**
 * Decore un {@link StorageBackend} : ses banques signalent chaque mutation acceptee au
 * {@link SyncCoordinator}. Rien d'autre ne change ; la fermeture reste a la charge du backend decore.
 */
public final class PublishingBackend implements StorageBackend {

    private final StorageBackend delegate;
    private final BankRepository banks;

    /**
     * @param delegate    backend reel
     * @param coordinator destinataire des changements enregistres
     * @param clock       horloge de la date des changements
     */
    public PublishingBackend(StorageBackend delegate, LocalChangeSink coordinator, Clock clock) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.banks = new PublishingBankRepository(delegate.banks(), coordinator, clock);
    }

    @Override
    public BankRepository banks() {
        return banks;
    }

    @Override
    public PendingDeliveryStore deliveries() {
        return delegate.deliveries();
    }

    @Override
    public ActiveBankStore activeBanks() {
        return delegate.activeBanks();
    }

    @Override
    public StorageContent content() {
        return delegate.content();
    }

    @Override
    public Optional<BankChangeFeed> changeFeed() {
        return delegate.changeFeed();
    }

    @Override
    public int schemaVersion() {
        return delegate.schemaVersion();
    }

    @Override
    public int checkSchemaVersion() {
        return delegate.checkSchemaVersion();
    }

    /**
     * Sans effet : le backend decore est ferme par son proprietaire.
     */
    @Override
    public void close() {
        // Le cycle de vie ferme le backend reel, pas son decorateur.
    }
}
