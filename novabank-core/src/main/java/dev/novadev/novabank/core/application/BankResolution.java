package dev.novadev.novabank.core.application;

import dev.novadev.novabank.api.entity.EntityMembership;
import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.operation.OperationType;
import dev.novadev.novabank.core.selection.BankSelector;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolution de la banque active d'un joueur : choix persiste (lecture en base, donc sur
 * l'executeur IO) puis regle de {@link BankSelector}.
 */
final class BankResolution {

    private BankResolution() {
    }

    /**
     * @param ports       collaborateurs
     * @param playerId    joueur
     * @param memberships appartenances lues sur le thread du joueur
     * @param rules       regles en vigueur
     * @return l'appartenance active, ou vide si le joueur n'en a aucune
     */
    static Optional<EntityMembership> active(OperationPorts ports, UUID playerId, List<EntityMembership> memberships,
                                             TransferRules rules) {
        if (memberships.isEmpty()) {
            return Optional.empty();
        }
        Optional<EntityRef> chosen = ports.activeBanks().find(playerId);
        return BankSelector.select(memberships, chosen, rules.defaultPriority());
    }

    /**
     * @param rules      regles en vigueur ({@code banks.roles})
     * @param hostRights droits decides par les plugins hotes, prioritaires
     * @param active     appartenance active
     * @param type       operation demandee
     * @return {@code true} si l'operation est permise
     */
    static boolean allowed(TransferRules rules, Map<EntityRef, Map<OperationType, Boolean>> hostRights,
                           EntityMembership active, OperationType type) {
        Boolean host = hostRights.getOrDefault(active.entity(), Map.of()).get(type);
        return host != null ? host : rules.rightsFor(active.entity().type()).allows(type, active.role());
    }
}
