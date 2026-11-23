package dev.novadev.novabank.core.application;

import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.api.entity.EntityMembership;
import dev.novadev.novabank.core.access.Permissions;
import dev.novadev.novabank.core.bank.Bank;
import dev.novadev.novabank.core.text.ConsoleText;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Consultation de la banque active : appartenances (thread joueur), puis choix et lecture
 * de la banque (IO). Lecture seule, hors verrou du joueur.
 */
final class InfoFlow extends Flow<BankView> {

    private final TransferRules rules;

    InfoFlow(OperationPorts ports, UUID playerId, TransferRules rules, Consumer<BankView> done) {
        super(ports, playerId, done, () -> { });
        this.rules = rules;
    }

    void start() {
        onPlayer(this::resolve, () -> { });
    }

    private void resolve() {
        if (!ports.permissions().has(playerId, Permissions.USE)) {
            finish(BankView.failed(QueryStatus.NO_PERMISSION));
            return;
        }
        List<EntityMembership> memberships = ports.ownership().memberships(playerId, rules.enabledTypes());
        onIo(() -> read(memberships));
    }

    private void read(List<EntityMembership> memberships) {
        Optional<EntityMembership> active = BankResolution.active(ports, playerId, memberships, rules);
        if (active.isEmpty()) {
            finish(new BankView(QueryStatus.NO_BANK, Optional.empty(), memberships, Optional.empty()));
            return;
        }
        Optional<Bank> bank = ports.banks().findBank(BankId.forOwner(active.get().entity()));
        BankSnapshot snapshot = BankSnapshot.of(active.get().entity(), bank, ports.ledger());
        finish(new BankView(QueryStatus.OK, active, memberships, Optional.of(snapshot)));
    }

    @Override
    void failed(RuntimeException failure) {
        LOGGER.log(System.Logger.Level.WARNING,
                ConsoleText.tr("Consultation de la banque du joueur {0} impossible : {1}",
                        playerId, failure));
        finish(BankView.failed(QueryStatus.STORAGE_ERROR));
    }
}
