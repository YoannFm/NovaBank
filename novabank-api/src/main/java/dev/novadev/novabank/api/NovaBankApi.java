package dev.novadev.novabank.api;

import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.api.operation.OperationInfo;
import dev.novadev.novabank.api.operation.OperationResult;
import dev.novadev.novabank.api.ranking.LeaderboardLine;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * API publique de NovaBank, obtenue par le gestionnaire de services de Bukkit :
 * <pre>{@code
 * RegisteredServiceProvider<NovaBankApi> provider =
 *         Bukkit.getServicesManager().getRegistration(NovaBankApi.class);
 * NovaBankApi api = provider.getProvider();
 * }</pre>
 *
 * <h2>Garanties</h2>
 * <ul>
 *   <li>Les methodes qui renvoient un {@link CompletableFuture} travaillent sur l'executeur d'entrees/sorties de
 *       NovaBank (base de donnees) : elles ne bloquent jamais le thread appelant, mais ne doivent pas non plus etre
 *       attendues ({@code join}) depuis le thread principal. Le futur s'acheve exceptionnellement si NovaBank est
 *       arrete ou si le stockage est indisponible.</li>
 *   <li>Rejouer un meme {@link dev.novadev.novabank.api.OperationId} ne produit jamais un second effet : le resultat est
 *       {@code DUPLICATE_OPERATION} avec les donnees de l'operation d'origine. Generez l'identifiant AVANT l'appel
 *       et conservez-le pour reessayer apres un echec reseau ou un arret.</li>
 *   <li>{@code deposit} et {@code withdraw} ne touchent PAS aux inventaires : l'appelant retire les items avant un
 *       depot et les donne apres un retrait reussi. C'est la mecanique de comptabilite ; les commandes et la GUI de
 *       NovaBank gerent les inventaires elles-memes.</li>
 *   <li>Les modifications sont notifiees par l'evenement asynchrone
 *       {@link dev.novadev.novabank.api.event.BankOperationEvent}.</li>
 * </ul>
 */
public interface NovaBankApi {

    /**
     * @param owner entite proprietaire
     * @return l'identifiant stable de sa banque (deterministe : le meme pour la meme entite sur tous les serveurs)
     */
    BankId bankId(EntityRef owner);

    /**
     * @param id banque
     * @return sa photographie, vide si elle n'existe pas (aucune operation n'a jamais eu lieu)
     */
    CompletableFuture<Optional<BankInfo>> bank(BankId id);

    /**
     * Depose des unites dans une banque (sans toucher aux inventaires). La banque est creee si elle n'existe pas.
     *
     * @param operationId identifiant unique de l'operation (idempotence)
     * @param owner       entite proprietaire de la banque
     * @param item        item depose, doit etre accepte par la configuration
     * @param quantity    unites, strictement positives
     * @return le resultat ; refus possibles : {@code NOT_ACCEPTED}, {@code INVALID_QUANTITY}
     */
    CompletableFuture<OperationResult> deposit(OperationId operationId, EntityRef owner, ItemKey item, long quantity);

    /**
     * Retire des unites d'une banque (sans toucher aux inventaires) : l'appelant donne ensuite les items.
     *
     * @param operationId identifiant unique de l'operation (idempotence)
     * @param owner       entite proprietaire de la banque
     * @param item        item retire
     * @param quantity    unites, strictement positives
     * @return le resultat ; refus possibles : {@code INSUFFICIENT_STOCK}, {@code UNKNOWN_BANK}, {@code INVALID_QUANTITY}
     */
    CompletableFuture<OperationResult> withdraw(OperationId operationId, EntityRef owner, ItemKey item, long quantity);

    /**
     * Lit un classement depuis le cache en memoire (pas d'acces a la base, utilisable depuis n'importe quel thread).
     *
     * @param type  type d'entite classe, par exemple {@code player}
     * @param limit nombre maximal de lignes
     * @return les meilleures banques, rang 1 en premier ; vide si le type n'est pas classe
     */
    List<LeaderboardLine> top(String type, int limit);

    /**
     * Cree la banque d'une entite si elle n'existe pas (operation idempotente).
     *
     * @param owner entite proprietaire
     * @return la banque, existante ou neuve
     */
    CompletableFuture<BankInfo> createBank(EntityRef owner);

    /**
     * Reinitialise la VALEUR d'une banque en conservant son stock (CDC 11). Reservee aux plugins de confiance : aucune
     * confirmation ni permission n'est demandee ici, c'est a l'appelant de les verifier.
     *
     * @param operationId identifiant unique de l'operation (idempotence)
     * @param owner       entite proprietaire
     * @return le resultat ; refus possible : {@code UNKNOWN_BANK}
     */
    CompletableFuture<OperationResult> resetValue(OperationId operationId, EntityRef owner);

    /**
     * @param operationId identifiant d'une operation
     * @return son etat dans le journal, vide si elle est inconnue
     */
    CompletableFuture<Optional<OperationInfo>> operation(OperationId operationId);

    /**
     * @return les types d'entite pris en charge (un fournisseur est enregistre), tries
     */
    java.util.SortedSet<String> entityTypes();

    /**
     * Enregistre le fournisseur d'appartenance d'un type d'entite (integration d'un plugin tiers). Le type doit ensuite
     * etre ajoute a {@code banks.entity-types} dans {@code config.yml} pour etre utilise.
     *
     * @param provider fournisseur
     * @return {@code true} si enregistre, {@code false} si le type est deja gere
     */
    boolean registerOwnershipProvider(dev.novadev.novabank.api.entity.OwnershipProvider provider);

    /**
     * @param entityType type d'entite dont retirer le fournisseur (plugin tiers desactive)
     */
    void unregisterOwnershipProvider(String entityType);
}
