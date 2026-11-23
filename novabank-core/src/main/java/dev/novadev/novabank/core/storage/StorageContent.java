package dev.novadev.novabank.core.storage;

import dev.novadev.novabank.core.bank.Bank;
import dev.novadev.novabank.core.journal.OperationRecord;
import dev.novadev.novabank.core.repository.StorageException;
import java.util.List;
import java.util.function.Consumer;

/**
 * Contenu complet d'un backend, lu ou ecrit en bloc : sert uniquement a la migration entre
 * backends (CDC 7.2), jamais aux operations des joueurs.
 *
 * <p>Lecture : chaque element est transmis tel quel, sans transformation (banque avec sa version,
 * son epoch de reset et ses lignes ; entree de journal dans son etat courant ; livraison avec sa
 * quantite deja remise). Les livraisons sont transmises dans leur ordre d'enregistrement ; pour
 * les autres elements, l'ordre est libre. A appeler sans ecriture concurrente (plugin en
 * maintenance) pour obtenir un etat coherent.
 *
 * <p>Ecriture : reservee a un backend vide ({@link #isEmpty()}), par lots, chaque lot ecrit de
 * facon durable avant le retour. Les elements sont enregistres a l'identique (aucune regle metier,
 * aucune version recalculee). Un echec laisse une copie partielle, signalee par
 * {@link StorageException}.
 */
public interface StorageContent {

    /**
     * @return {@code true} si le backend ne contient ni banque, ni operation, ni livraison, ni
     *         banque active
     * @throws StorageException si la base est inaccessible
     */
    boolean isEmpty();

    /**
     * @param sink recepteur de chaque banque
     * @throws StorageException si la lecture echoue
     */
    void exportBanks(Consumer<Bank> sink);

    /**
     * @param sink recepteur de chaque entree du journal
     * @throws StorageException si la lecture echoue
     */
    void exportOperations(Consumer<OperationRecord> sink);

    /**
     * @param sink recepteur de chaque livraison, par ordre d'enregistrement
     * @throws StorageException si la lecture echoue
     */
    void exportDeliveries(Consumer<StoredDelivery> sink);

    /**
     * @param sink recepteur de chaque banque active
     * @throws StorageException si la lecture echoue
     */
    void exportActiveBanks(Consumer<ActiveBankChoice> sink);

    /**
     * @param banks banques a enregistrer telles quelles (identifiants absents de la cible)
     * @throws StorageException si l'ecriture echoue
     */
    void importBanks(List<Bank> banks);

    /**
     * @param records entrees a enregistrer telles quelles ; leurs banques sont deja importees
     * @throws StorageException si l'ecriture echoue
     */
    void importOperations(List<OperationRecord> records);

    /**
     * @param deliveries livraisons a enregistrer, dans l'ordre d'enregistrement d'origine
     * @throws StorageException si l'ecriture echoue
     */
    void importDeliveries(List<StoredDelivery> deliveries);

    /**
     * @param choices banques actives a enregistrer
     * @throws StorageException si l'ecriture echoue
     */
    void importActiveBanks(List<ActiveBankChoice> choices);
}
