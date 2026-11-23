package dev.novadev.novabank.core.storage;

import dev.novadev.novabank.core.delivery.PendingDeliveryStore;
import dev.novadev.novabank.core.repository.BankRepository;
import dev.novadev.novabank.core.repository.StorageException;
import dev.novadev.novabank.core.selection.ActiveBankStore;
import dev.novadev.novabank.core.sync.BankChangeFeed;
import java.util.Optional;

/**
 * Backend de stockage ouvert : fournit les ports de persistance du coeur et detient les
 * ressources techniques (pool, client reseau) liberees par {@link #close()}.
 *
 * <p>Les ports renvoyes sont surs entre threads et restent les memes pendant toute la vie du
 * backend. Toute panne technique est signalee par {@link StorageException}.
 */
public interface StorageBackend extends AutoCloseable {

    /**
     * @return les banques et leur journal
     */
    BankRepository banks();

    /**
     * @return la file des livraisons en attente
     */
    PendingDeliveryStore deliveries();

    /**
     * @return la banque active choisie par chaque joueur
     */
    ActiveBankStore activeBanks();

    /**
     * @return le contenu complet, pour la migration entre backends
     */
    StorageContent content();

    /**
     * @return le flux de changements des banques, utilise par la synchronisation multi-serveur ;
     *         vide pour un backend local (SQLite, YAML, JSON) qui ne peut pas etre partage
     */
    default Optional<BankChangeFeed> changeFeed() {
        return Optional.empty();
    }

    /**
     * @return la version de schema constatee (ou appliquee) a l'ouverture
     */
    int schemaVersion();

    /**
     * Verifie que la base repond en relisant sa version de schema : vraie requete, a ne pas
     * appeler depuis un thread du serveur.
     *
     * @return la version de schema lue dans la base
     * @throws StorageException si la base est inaccessible
     */
    int checkSchemaVersion();

    /**
     * Libere les ressources du backend. Les ports ne doivent plus etre utilises ensuite.
     *
     * @throws StorageException si la fermeture echoue
     */
    @Override
    void close();
}
