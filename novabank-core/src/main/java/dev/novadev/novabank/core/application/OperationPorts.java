package dev.novadev.novabank.core.application;

import dev.novadev.novabank.core.delivery.PendingDeliveryStore;
import dev.novadev.novabank.core.ledger.BankLedger;
import dev.novadev.novabank.core.ownership.OwnershipRegistry;
import dev.novadev.novabank.core.repository.BankRepository;
import dev.novadev.novabank.core.selection.ActiveBankStore;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Collaborateurs des operations des joueurs, assembles par la plateforme.
 *
 * @param ledger      service metier des banques (appele sur l'executeur IO uniquement)
 * @param banks       lecture des banques et du journal (executeur IO uniquement)
 * @param deliveries  livraisons en attente (executeur IO uniquement)
 * @param activeBanks banque active choisie (executeur IO uniquement)
 * @param ownership   fournisseurs d'appartenance (thread du joueur)
 * @param inventory   inventaires (thread du joueur)
 * @param playerData  sauvegarde des donnees joueur (thread du joueur)
 * @param permissions permissions (thread du joueur)
 * @param scheduler   planificateur
 * @param rules       regles en vigueur (lues au debut de chaque operation)
 * @param gate        verrou et delai par joueur
 * @param retry       nouvelles tentatives des appels au stockage
 * @param drain       suivi des flux en cours et phase d'arret
 * @param actors      joueur a l\'origine de chaque operation lancee sur ce serveur
 */
public record OperationPorts(BankLedger ledger, BankRepository banks, PendingDeliveryStore deliveries,
                             ActiveBankStore activeBanks, OwnershipRegistry ownership, InventoryGateway inventory,
                             PlayerDataSaver playerData, PermissionChecker permissions, TaskScheduler scheduler,
                             Supplier<TransferRules> rules, PlayerOperationGate gate, StorageRetry retry,
                             FlowDrain drain, OperationActors actors) {

    /**
     * @throws NullPointerException si un collaborateur est nul
     */
    public OperationPorts {
        Objects.requireNonNull(ledger, "ledger");
        Objects.requireNonNull(banks, "banks");
        Objects.requireNonNull(deliveries, "deliveries");
        Objects.requireNonNull(activeBanks, "activeBanks");
        Objects.requireNonNull(ownership, "ownership");
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(playerData, "playerData");
        Objects.requireNonNull(permissions, "permissions");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(gate, "gate");
        Objects.requireNonNull(retry, "retry");
        Objects.requireNonNull(drain, "drain");
        Objects.requireNonNull(actors, "actors");
    }
}
