package dev.novadev.novabank.storage.file;

import dev.novadev.novabank.core.bank.Bank;
import dev.novadev.novabank.core.journal.OperationRecord;
import dev.novadev.novabank.core.storage.ActiveBankChoice;
import dev.novadev.novabank.core.storage.StoredDelivery;
import java.util.List;

/**
 * Ensemble d'elements a enregistrer ensemble : une ligne du journal d'ecriture, ou le contenu
 * complet d'un instantane. Chaque element est l'etat COMPLET apres la mutation (jamais une
 * difference) : rejouer une ligne deja appliquee ne change rien.
 *
 * @param banks       banques, avec version, epoch et lignes
 * @param operations  entrees du journal d'operations
 * @param deliveries  livraisons, quantite remise comprise
 * @param activeBanks banques actives
 */
record Commit(List<Bank> banks, List<OperationRecord> operations, List<StoredDelivery> deliveries,
              List<ActiveBankChoice> activeBanks) {

    /** Copie defensive des listes. */
    Commit {
        banks = List.copyOf(banks);
        operations = List.copyOf(operations);
        deliveries = List.copyOf(deliveries);
        activeBanks = List.copyOf(activeBanks);
    }

    /**
     * @param bank   banque apres la mutation
     * @param record entree de journal de la mutation
     * @return la mutation d'une banque et de son journal
     */
    static Commit of(Bank bank, OperationRecord record) {
        return new Commit(List.of(bank), List.of(record), List.of(), List.of());
    }

    /**
     * @param bank banque neuve
     * @return la creation d'une banque
     */
    static Commit of(Bank bank) {
        return new Commit(List.of(bank), List.of(), List.of(), List.of());
    }

    /**
     * @param delivery livraison apres l'ajout ou la remise
     * @return la mutation d'une livraison
     */
    static Commit of(StoredDelivery delivery) {
        return new Commit(List.of(), List.of(), List.of(delivery), List.of());
    }

    /**
     * @param choice banque active
     * @return l'enregistrement d'une banque active
     */
    static Commit of(ActiveBankChoice choice) {
        return new Commit(List.of(), List.of(), List.of(), List.of(choice));
    }
}
