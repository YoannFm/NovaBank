package dev.novadev.novabank.bukkit.command;

import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.api.OperationId;
import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.api.valuation.ValuationMode;
import dev.novadev.novabank.bukkit.testing.TestLanguages;
import dev.novadev.novabank.core.application.BankAdministration;
import dev.novadev.novabank.core.delivery.UndeliveredPolicy;
import dev.novadev.novabank.core.ledger.BankLedger;
import dev.novadev.novabank.core.testing.InMemoryBankRepository;
import dev.novadev.novabank.core.testing.InMemoryPendingDeliveryStore;
import dev.novadev.novabank.core.testing.ManualScheduler;
import dev.novadev.novabank.core.testing.MutablePriceCatalog;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BankAdminCommandsTest {

    private static final UUID YO = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final ItemKey STONE = ItemKey.minecraft("stone");

    private final ManualScheduler scheduler = new ManualScheduler();
    private final InMemoryBankRepository repository = new InMemoryBankRepository();
    private final BankLedger ledger = new BankLedger(repository, new MutablePriceCatalog().price(STONE, "2"),
            new InMemoryPendingDeliveryStore(), UndeliveredPolicy.PENDING_DELIVERY, Clock.systemUTC());
    private final RecordingActor admin = new RecordingActor(true);
    private final BankAdminCommands commands = new BankAdminCommands(
            new BankAdministration(ledger, repository, scheduler),
            (name, done) -> done.accept(name.equalsIgnoreCase("yo")
                    ? Optional.of(new PlayerLookup.KnownPlayer(YO, "Yo")) : Optional.empty()),
            new PendingConfirmations<>(Clock.systemUTC()), TestLanguages::french,
            () -> new TransferMessages(TestLanguages.french(), CommandArguments::shortName, EntityRef::id,
                    UndeliveredPolicy.PENDING_DELIVERY, 64),
            CommandArguments::shortName);

    @Test
    void joueur_inconnu_repond_proprement() {
        commands.bank(admin, "novabank", "Fantome");
        scheduler.runAll();

        assertThat(admin.received()).containsExactly("NovaBank » Joueur inconnu : Fantome.");
    }

    @Test
    void consultation_d_une_banque_vide_puis_remplie() {
        commands.bank(admin, "novabank", "Yo");
        scheduler.runAll();
        depositStones(5);
        commands.bank(admin, "novabank", "yo");
        scheduler.runAll();

        assertThat(admin.received()).contains("Cette banque n'a encore jamais servi.", "Valeur : 10",
                " - stone : 5");
    }

    @Test
    void reset_exige_une_confirmation_puis_efface_la_valeur() {
        depositStones(5);

        commands.reset(admin, "novabank", "Yo", true);
        commands.reset(admin, "novabank", "Yo", false);
        commands.reset(admin, "novabank", "Yo", true);
        scheduler.runAll();

        assertThat(admin.received().get(0)).contains("Aucun reset en attente");
        assertThat(admin.received().get(1)).contains("/novabank reset Yo confirm", "30 s");
        assertThat(admin.received().get(2)).contains("reinitialisee", "10 effaces");
        assertThat(repository.findBank(BankId.forOwner(EntityRef.player(YO))).orElseThrow().resetEpoch()).isEqualTo(1);
    }

    @Test
    void historique_pagine() {
        for (int i = 0; i < 11; i++) {
            depositStones(1);
        }

        commands.history(admin, "novabank", "Yo", 1);
        scheduler.runAll();

        assertThat(admin.received().get(0)).isEqualTo("NovaBank » Historique de Yo - page 1");
        assertThat(admin.received()).hasSize(12);
        assertThat(admin.received().get(1)).contains("Depot", "stone", "x1", "(2)");
        assertThat(admin.received()).last().asString().contains("/novabank history Yo 2");
    }

    private void depositStones(long quantity) {
        BankId bankId = BankId.forOwner(EntityRef.player(YO));
        ledger.openBank(bankId, EntityRef.player(YO), ValuationMode.DERIVED);
        ledger.deposit(OperationId.random(), bankId, STONE, quantity);
    }
}
