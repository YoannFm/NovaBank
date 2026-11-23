package dev.novadev.novabank.core.application;

import dev.novadev.novabank.api.operation.OperationResult;
import dev.novadev.novabank.core.journal.OperationRecord;
import dev.novadev.novabank.core.ledger.BankLedger;
import dev.novadev.novabank.core.repository.BankRepository;
import dev.novadev.novabank.core.text.ConsoleText;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Solde au demarrage les retraits restes RESERVED (arret du serveur entre la reservation
 * et la confirmation) : confirmation avec {@code livre = 0}, le reste suivant la
 * politique D13 (livraison differee ou retour en banque).
 *
 * <p>Stockage exclusif (un seul serveur) : {@link #reconcile(Instant)} au demarrage, sur les
 * retraits reserves AVANT le demarrage (la reconciliation peut tourner sur l'executeur IO
 * pendant que de nouveaux retraits commencent).
 *
 * <p>Stockage partage entre serveurs : un retrait reserve peut etre EN COURS sur un autre
 * serveur, le solder a 0 remis doublerait les items deja donnes. Le journal ne dit pas quel
 * serveur l'a reserve ; {@link #reconcileStale(Instant)}, appele periodiquement, ne solde donc
 * que les retraits reserves depuis plus de {@link #SHARED_STORAGE_GRACE} : un retrait en cours
 * dure quelques secondes, un retrait plus ancien est orphelin (serveur arrete ou plante).
 *
 * <p>Risque residuel documente : si le serveur a sauvegarde l'inventaire du joueur apres
 * la remise des items mais avant la confirmation, puis s'est arrete brutalement, les items
 * remis sont aussi traites selon D13 (duplication bornee a ce retrait, fenetre d'un tick).
 */
public final class WithdrawalReconciler {

    /**
     * Age minimal d'un retrait reserve avant son solde sur un stockage partage : tres au-dela de la duree d'un
     * retrait en cours (reservation, remise au tick suivant, confirmation avec nouvelles tentatives) et de l'ecart
     * d'horloge tolere entre serveurs (l'horodatage vient du serveur qui a reserve).
     */
    public static final Duration SHARED_STORAGE_GRACE = Duration.ofMinutes(2);

    private static final System.Logger LOGGER = System.getLogger("NovaBank");

    private final BankLedger ledger;
    private final BankRepository banks;

    /**
     * @param ledger service metier
     * @param banks  journal
     */
    public WithdrawalReconciler(BankLedger ledger, BankRepository banks) {
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.banks = Objects.requireNonNull(banks, "banks");
    }

    /**
     * Stockage partage : solde les retraits reserves depuis plus de {@link #SHARED_STORAGE_GRACE}. A appeler sur
     * l'executeur d'entrees/sorties.
     *
     * @param now instant courant
     * @return le bilan
     */
    public ReconciliationReport reconcileStale(Instant now) {
        return reconcile(now.minus(SHARED_STORAGE_GRACE));
    }

    /**
     * A appeler sur l'executeur d'entrees/sorties.
     *
     * @param startedAt seuls les retraits reserves avant cet instant sont soldes (le demarrage, pour un stockage
     *                  exclusif)
     * @return le bilan
     */
    public ReconciliationReport reconcile(Instant startedAt) {
        Objects.requireNonNull(startedAt, "startedAt");
        int settled = 0;
        int failed = 0;
        long units = 0L;
        for (OperationRecord record : banks.reservedWithdrawals()) {
            if (!record.timestamp().isBefore(startedAt)) {
                continue;
            }
            try {
                OperationResult result = ledger.confirmWithdrawal(record.operationId(), 0L);
                if (result.isEffective()) {
                    settled++;
                    units += record.quantity();
                    LOGGER.log(System.Logger.Level.WARNING,
                            ConsoleText.tr("Retrait {0} reste reserve ({1} x {2}, joueur {3}) solde avec 0 remis",
                                    record.operationId(), record.quantity(), record.itemKey(), record.withdrawal().recipient()));
                } else {
                    failed++;
                }
            } catch (RuntimeException failure) {
                failed++;
                LOGGER.log(System.Logger.Level.ERROR, ConsoleText.tr("Solde du retrait {0} impossible : {1}",
                        record.operationId(), failure));
            }
        }
        return new ReconciliationReport(settled, failed, units);
    }
}
