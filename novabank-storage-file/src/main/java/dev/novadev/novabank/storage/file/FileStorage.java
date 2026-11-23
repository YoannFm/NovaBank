package dev.novadev.novabank.storage.file;

import dev.novadev.novabank.core.delivery.PendingDeliveryStore;
import dev.novadev.novabank.core.repository.BankRepository;
import dev.novadev.novabank.core.repository.StorageException;
import dev.novadev.novabank.core.selection.ActiveBankStore;
import dev.novadev.novabank.core.storage.StorageBackend;
import dev.novadev.novabank.core.storage.StorageContent;
import java.nio.file.Path;

/**
 * Point d'entree du stockage fichier YAML ou JSON ({@link StorageBackend}), mono-serveur.
 * Proprietaire de ses fichiers et de leur verrou : {@link #close()} ecrit l'instantane final et
 * libere le verrou.
 */
public final class FileStorage implements StorageBackend {

    /** Version du format des fichiers (instantane et journal). */
    public static final int FORMAT_VERSION = Integer.parseInt(SnapshotFile.FORMAT_VERSION);

    private final FileStore store;
    private final BankRepository banks;
    private final PendingDeliveryStore deliveries;
    private final ActiveBankStore activeBanks;
    private final StorageContent content;

    private FileStorage(FileStore store, FileStorageOptions options) {
        this.store = store;
        this.banks = new FileBankRepository(store);
        this.deliveries = new FileDeliveryStore(store, options.clock());
        this.activeBanks = new FileActiveBankStore(store);
        this.content = new FileContent(store);
    }

    /**
     * Ouvre le stockage : verrou exclusif, instantane, rejeu du journal d'ecriture.
     *
     * @param options reglages
     * @return le stockage pret
     * @throws StorageException si les donnees sont utilisees par un autre processus, illisibles
     *                          ou corrompues
     */
    public static FileStorage open(FileStorageOptions options) {
        return new FileStorage(FileStore.open(options), options);
    }

    @Override
    public BankRepository banks() {
        return banks;
    }

    @Override
    public PendingDeliveryStore deliveries() {
        return deliveries;
    }

    @Override
    public ActiveBankStore activeBanks() {
        return activeBanks;
    }

    @Override
    public StorageContent content() {
        return content;
    }

    @Override
    public int schemaVersion() {
        return FORMAT_VERSION;
    }

    /**
     * @return la version du format, si le stockage accepte encore des ecritures
     * @throws StorageException apres un echec d'ecriture du journal ou une fermeture
     */
    @Override
    public int checkSchemaVersion() {
        store.requireWritable();
        return FORMAT_VERSION;
    }

    /**
     * @return fichier de l'instantane (diagnostic)
     */
    public Path snapshotFile() {
        return store.snapshotPath();
    }

    /**
     * @return fichier du journal d'ecriture (diagnostic, tests)
     */
    public Path journalFile() {
        return store.journalPath();
    }

    @Override
    public void close() {
        store.close();
    }
}
