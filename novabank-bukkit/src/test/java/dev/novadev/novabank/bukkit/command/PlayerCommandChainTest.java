package dev.novadev.novabank.bukkit.command;

import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.api.valuation.ValuationMode;
import dev.novadev.novabank.bukkit.testing.TestLanguages;
import dev.novadev.novabank.core.application.BankService;
import dev.novadev.novabank.core.application.FlowDrain;
import dev.novadev.novabank.core.application.OperationActors;
import dev.novadev.novabank.core.application.OperationPorts;
import dev.novadev.novabank.core.application.PlayerOperationGate;
import dev.novadev.novabank.core.application.StorageRetry;
import dev.novadev.novabank.core.application.TransferRules;
import dev.novadev.novabank.core.delivery.UndeliveredPolicy;
import dev.novadev.novabank.core.ledger.BankLedger;
import dev.novadev.novabank.core.ownership.OwnershipRegistry;
import dev.novadev.novabank.core.ownership.PlayerOwnershipProvider;
import dev.novadev.novabank.core.testing.EventLog;
import dev.novadev.novabank.core.testing.FakeInventory;
import dev.novadev.novabank.core.testing.InMemoryActiveBankStore;
import dev.novadev.novabank.core.testing.InMemoryPendingDeliveryStore;
import dev.novadev.novabank.core.testing.ManualScheduler;
import dev.novadev.novabank.core.testing.MutablePriceCatalog;
import dev.novadev.novabank.core.testing.ObservedBankRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * Chaine complete sans serveur : commande saisie, lecture des arguments, couche
 * application sur doublures, textes envoyes au joueur.
 */
class PlayerCommandChainTest {

    private static final ItemKey STONE = ItemKey.minecraft("stone");

    private final RecordingActor player = new RecordingActor(false);
    private final ManualScheduler scheduler = new ManualScheduler().connect(player.id());
    private final EventLog log = new EventLog();
    private final FakeInventory inventory = new FakeInventory(log);
    private final KnownStock stock = new KnownStock();
    private final TransferRules rules = new TransferRules(List.of("player"), List.of("player"), Map.of(),
            ValuationMode.DERIVED, List.of(STONE), 100, Duration.ZERO, true);
    private final PlayerCommandRouter router;

    PlayerCommandChainTest() {
        ObservedBankRepository repository = new ObservedBankRepository(log);
        InMemoryPendingDeliveryStore deliveries = new InMemoryPendingDeliveryStore();
        BankLedger ledger = new BankLedger(repository, new MutablePriceCatalog().price(STONE, "0.5"), deliveries,
                UndeliveredPolicy.PENDING_DELIVERY, Clock.systemUTC());
        OwnershipRegistry ownership = new OwnershipRegistry();
        ownership.register(new PlayerOwnershipProvider(id -> Optional.of("Yo")));
        BankService service = new BankService(new OperationPorts(ledger, repository, deliveries,
                new InMemoryActiveBankStore(), ownership, inventory, id -> log.add("sauvegarde"),
                (id, permission) -> true, scheduler, () -> rules, new PlayerOperationGate(Clock.systemUTC()),
                new StorageRetry(1, Duration.ZERO), new FlowDrain(), new OperationActors()));
        Supplier<TransferMessages> messages = () -> new TransferMessages(TestLanguages.french(),
                CommandArguments::shortName, ownership::displayName, UndeliveredPolicy.PENDING_DELIVERY, 100);
        router = new PlayerCommandRouter(new PlayerCommandActions(service, messages, stock, rules::enabledTypes,
                id -> log.add("gui")),
                new FakeCompletions(), TestLanguages::french);
    }

    @Test
    void depot_retrait_consultation_et_choix_de_banque() {
        inventory.put(player.id(), STONE, 64);

        run("deposit", "stone", "40");
        run("withdraw", "stone", "15");
        run("info");
        run("select", "island");
        run("select", "player");

        assertThat(player.received()).containsExactly(
                "NovaBank » Depot de 40 stone dans Yo : +20 (valeur : 20).",
                "NovaBank » Retrait de 15 stone de Yo : -7,5 (valeur : 12,5).",
                "NovaBank » Banque active : Yo (player)",
                "Valeur : 12,5",
                "Stock :",
                " - stone : 25",
                "NovaBank » Type de banque inconnu : island. Types disponibles : player.",
                "NovaBank » Banque active : Yo (player)");
        assertThat(inventory.quantity(player.id(), STONE)).isEqualTo(39);
        assertThat(stock.of(player.id())).containsExactly(STONE);
        assertThat(EntityRef.player(player.id()).type()).isEqualTo("player");
    }

    private void run(String... args) {
        router.execute(player, "bank", args);
        scheduler.runAll();
    }
}
