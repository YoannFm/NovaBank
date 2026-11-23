package dev.novadev.novabank.core.repository;

import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.api.OperationId;
import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.valuation.ValuationMode;
import dev.novadev.novabank.core.bank.Bank;
import dev.novadev.novabank.core.journal.OperationRecord;
import java.util.List;
import java.util.Optional;

/**
 * Port de persistance : banques et journal d'operations, source de verite.
 *
 * <h2>Contrat</h2>
 * <ul>
 *   <li>{@link #apply} et {@link #settle} sont <b>atomiques</b> : lecture de l'etat,
 *       decision, ecriture de la banque et de l'entree de journal forment une seule
 *       unite. Deux mutations concurrentes sur la meme banque ne perdent jamais de
 *       mise a jour.</li>
 *   <li>Chaque mutation acceptee enregistre la banque avec la version lue + 1 ; un
 *       refus ou un rejeu ne modifie ni la banque, ni sa version, ni le journal.</li>
 *   <li>Un identifiant d'operation n'est journalise qu'une fois, toutes banques
 *       confondues : si {@link #apply} recoit un identifiant deja present, il renvoie
 *       {@link ApplyOutcome.Duplicate} avec l'entree d'origine, sans appeler la mutation.</li>
 *   <li>Les mutations sont pures et peuvent etre appelees plusieurs fois
 *       (verrou optimiste) ; une exception levee par une mutation annule l'unite.</li>
 *   <li>L'entree acceptee doit porter l'identifiant d'operation et la banque demandes,
 *       et la banque acceptee garder son proprietaire et son mode de valorisation,
 *       sinon {@link IllegalStateException}.</li>
 *   <li>Une panne technique du backend est signalee par {@link StorageException},
 *       apres annulation de l'unite en cours.</li>
 * </ul>
 *
 * <p>Implementations : transaction SQL ; MongoDB en transaction multi-documents (replica set)
 * ou, sur un serveur autonome, mise a jour du document banque conditionnee sur sa version qui
 * embarque l'entree de journal en attente, propagee ensuite au journal (module
 * {@code novabank-storage-mongo}) ; verrou par banque pour les fichiers (a venir). Une implementation
 * qui abandonne apres des conflits repetes renvoie
 * {@link ApplyOutcome.Rejected} avec {@code CONCURRENT_MODIFICATION}.
 */
public interface BankRepository {

    /**
     * Lit l'etat courant d'une banque.
     *
     * @param bankId banque recherchee
     * @return la banque, ou vide si elle n'existe pas
     */
    Optional<Bank> findBank(BankId bankId);

    /**
     * Cree une banque vide en version 0, de facon idempotente.
     *
     * @param bankId        identifiant de la banque
     * @param owner         proprietaire
     * @param valuationMode mode de valorisation
     * @return la banque creee, ou la banque existante telle quelle si l'identifiant
     *         etait deja pris (ses attributs ne sont pas modifies)
     */
    Bank createBank(BankId bankId, EntityRef owner, ValuationMode valuationMode);

    /**
     * Lit une entree du journal.
     *
     * @param operationId operation recherchee
     * @return l'entree, ou vide si l'operation est inconnue
     */
    Optional<OperationRecord> findOperation(OperationId operationId);

    /**
     * Applique atomiquement une operation nouvelle.
     *
     * @param operationId identifiant de l'operation
     * @param bankId      banque visee
     * @param mutation    decision a prendre sur l'etat courant ; elle ne repond jamais
     *                    {@link MutationDecision.AlreadyApplied} (le rejeu est detecte par le stockage)
     * @return {@link ApplyOutcome.Duplicate} si l'operation existe deja,
     *         {@link ApplyOutcome.Rejected} avec {@code UNKNOWN_BANK} si la banque
     *         n'existe pas, sinon l'issue de la decision
     */
    ApplyOutcome apply(OperationId operationId, BankId bankId, BankMutation mutation);

    /**
     * Fait evoluer atomiquement une operation deja journalisee et sa banque.
     *
     * @param operationId operation a faire evoluer
     * @param transition  decision a prendre sur l'entree et la banque courantes
     * @return {@link ApplyOutcome.Rejected} avec {@code UNKNOWN_OPERATION} si l'operation
     *         est inconnue, {@link ApplyOutcome.Duplicate} si la transition repond
     *         {@link MutationDecision.AlreadyApplied}, sinon l'issue de la decision
     */
    ApplyOutcome settle(OperationId operationId, RecordTransition transition);

    /**
     * Liste les retraits restes {@code RESERVED} : livraison commencee mais jamais soldee
     * (arret du serveur entre la reservation et la confirmation, par exemple).
     *
     * <p>Sert a la reconciliation au demarrage : la plateforme decide de les confirmer
     * ou de les annuler selon sa politique ; le stockage ne prend aucune decision.
     *
     * @return les entrees reservees, par horodatage croissant puis par identifiant
     *         d'operation (ordre du texte de l'UUID)
     */
    List<OperationRecord> reservedWithdrawals();

    /**
     * Journal d'une banque, du plus recent au plus ancien, par page.
     *
     * @param bankId banque
     * @param offset nombre d'entrees a sauter, positif ou nul
     * @param limit  nombre maximal d'entrees, strictement positif
     * @return les entrees, par horodatage decroissant puis identifiant d'operation
     *         decroissant (ordre du texte de l'UUID) ; vide si la banque est inconnue
     * @throws IllegalArgumentException si {@code offset} ou {@code limit} est hors bornes
     */
    List<OperationRecord> history(BankId bankId, int offset, int limit);
}
