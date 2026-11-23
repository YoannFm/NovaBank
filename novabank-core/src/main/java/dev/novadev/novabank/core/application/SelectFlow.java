package dev.novadev.novabank.core.application;

import dev.novadev.novabank.api.entity.EntityMembership;
import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.core.access.Permissions;
import dev.novadev.novabank.core.selection.BankSelector;
import dev.novadev.novabank.core.text.ConsoleText;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Choix de la banque active : appartenance du type demande (thread joueur), puis
 * enregistrement du choix (IO).
 */
final class SelectFlow extends Flow<SelectionResult> {

    private final String entityType;
    private final String command;
    private final TransferRules rules;

    SelectFlow(OperationPorts ports, UUID playerId, String entityType, String command, TransferRules rules,
               Consumer<SelectionResult> done) {
        super(ports, playerId, done, () -> { });
        this.entityType = entityType;
        this.command = command;
        this.rules = rules;
    }

    void start() {
        onPlayer(this::resolve, () -> { });
    }

    private void resolve() {
        if (!ports.permissions().has(playerId, Permissions.USE)) {
            finish(SelectionResult.failed(QueryStatus.NO_PERMISSION));
            return;
        }
        if (!rules.enabledTypes().contains(entityType)) {
            finish(SelectionResult.failed(QueryStatus.UNKNOWN_TYPE));
            return;
        }
        List<EntityMembership> memberships = ports.ownership().memberships(playerId, rules.enabledTypes());
        if (BankSelector.firstOfType(memberships, entityType).isEmpty()) {
            finish(SelectionResult.failed(QueryStatus.NOT_MEMBER));
            return;
        }
        Optional<EntityRef> preferred = ports.ownership().preferred(playerId, entityType, command);
        onIo(() -> {
            Optional<EntityMembership> membership = BankSelector.selectOfType(memberships, entityType, preferred,
                    ports.activeBanks().find(playerId));
            ports.activeBanks().save(playerId, membership.get().entity());
            finish(new SelectionResult(QueryStatus.OK, membership));
        });
    }

    @Override
    void failed(RuntimeException failure) {
        LOGGER.log(System.Logger.Level.WARNING, ConsoleText.tr("Choix de banque du joueur {0} impossible : {1}",
                playerId, failure));
        finish(SelectionResult.failed(QueryStatus.STORAGE_ERROR));
    }
}
