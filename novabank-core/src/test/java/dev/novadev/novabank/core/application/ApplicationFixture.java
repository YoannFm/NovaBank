package dev.novadev.novabank.core.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.api.entity.BankRole;
import dev.novadev.novabank.api.entity.EntityMembership;
import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.entity.OwnershipProvider;
import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.api.valuation.ValuationMode;
import dev.novadev.novabank.core.access.RoleRights;
import dev.novadev.novabank.core.bank.Bank;
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
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Montage de {@link BankService} sur des doublures : planificateur manuel, inventaire en
 * memoire, banque en memoire instrumentee, horloge reglable.
 */
final class ApplicationFixture {

    static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-00000000cafe");
    static final ItemKey STONE = ItemKey.minecraft("stone");
    static final ItemKey DIAMOND = ItemKey.minecraft("diamond_block");
    static final ItemKey DIRT = ItemKey.minecraft("dirt");
    static final EntityRef PERSONAL = EntityRef.player(PLAYER);
    static final EntityRef ISLAND = new EntityRef("island", "ile-42");
    static final Instant START = Instant.parse("2026-10-27T12:00:00Z");

    final EventLog log = new EventLog();
    final ManualScheduler scheduler = new ManualScheduler().connect(PLAYER);
    final ObservedBankRepository repository = new ObservedBankRepository(log);
    final MutablePriceCatalog catalog = new MutablePriceCatalog().price(STONE, "1").price(DIAMOND, "10");
    final InMemoryPendingDeliveryStore deliveries = new InMemoryPendingDeliveryStore();
    final InMemoryActiveBankStore activeBanks = new InMemoryActiveBankStore();
    final OwnershipRegistry ownership = new OwnershipRegistry();
    final FakeInventory inventory = new FakeInventory(log);
    final Set<String> deniedPermissions = new HashSet<>();
    final AtomicReference<Instant> now = new AtomicReference<>(START);
    final Map<UUID, List<EntityMembership>> islandMembers = new HashMap<>();
    final FlowDrain drain = new FlowDrain();
    final BankLedger ledger;
    final BankService service;
    final BankAdministration administration;
    TransferRules rules;

    ApplicationFixture() {
        this(UndeliveredPolicy.PENDING_DELIVERY);
    }

    ApplicationFixture(UndeliveredPolicy policy) {
        ledger = new BankLedger(repository, catalog, deliveries, policy, Clock.fixed(START, ZoneOffset.UTC));
        rules = new TransferRules(List.of(EntityRef.PLAYER, "island"), List.of(EntityRef.PLAYER, "island"),
                Map.of(), ValuationMode.DERIVED, List.of(STONE, DIAMOND), 100L, Duration.ofMillis(500), true);
        ownership.register(new PlayerOwnershipProvider(id -> Optional.of("Yo")));
        ownership.register(new IslandProvider());
        Clock clock = new Clock() {
            @Override
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now.get();
            }
        };
        OperationPorts ports = new OperationPorts(ledger, repository, deliveries, activeBanks, ownership, inventory,
                playerId -> {
                    assertThat(ManualScheduler.context()).isEqualTo(ManualScheduler.Context.PLAYER);
                    log.add("sauvegarde:" + playerId);
                },
                (playerId, permission) -> !deniedPermissions.contains(permission), scheduler, () -> rules,
                new PlayerOperationGate(clock), new StorageRetry(3, Duration.ZERO), drain, new OperationActors());
        service = new BankService(ports);
        administration = new BankAdministration(ledger, repository, scheduler);
    }

    void withRoles(String type, RoleRights rights) {
        rules = new TransferRules(rules.enabledTypes(), rules.defaultPriority(), Map.of(type, rights),
                rules.valuationMode(), rules.acceptedItems(), rules.maxQuantityPerOperation(), rules.cooldown(),
                rules.savePlayerData());
    }

    void withPriority(List<String> priority) {
        rules = new TransferRules(rules.enabledTypes(), priority, rules.roles(), rules.valuationMode(),
                rules.acceptedItems(), rules.maxQuantityPerOperation(), rules.cooldown(), rules.savePlayerData());
    }

    void withoutPlayerDataSave() {
        rules = new TransferRules(rules.enabledTypes(), rules.defaultPriority(), rules.roles(), rules.valuationMode(),
                rules.acceptedItems(), rules.maxQuantityPerOperation(), rules.cooldown(), false);
    }

    /** Decisions du plugin d'iles (privilege, flag) par operation ; absente = regle banks.roles. */
    final Map<dev.novadev.novabank.api.operation.OperationType, Boolean> hostRights = new java.util.HashMap<>();

    void joinIsland(EntityRef island, BankRole role) {
        islandMembers.computeIfAbsent(PLAYER, id -> new ArrayList<>()).add(new EntityMembership(island, role));
    }

    void leaveIslands() {
        islandMembers.remove(PLAYER);
    }

    void advance(Duration duration) {
        now.updateAndGet(instant -> instant.plus(duration));
    }

    List<TransferResult> deposit(ItemChoice choice, Amount amount) {
        List<List<TransferResult>> received = new ArrayList<>();
        service.deposit(PLAYER, choice, amount, received::add);
        scheduler.runAll();
        assertThat(received).hasSize(1);
        return received.get(0);
    }

    TransferResult depositOne(ItemKey item, long quantity) {
        List<TransferResult> results = deposit(ItemChoice.of(item), Amount.of(quantity));
        assertThat(results).hasSize(1);
        advance(Duration.ofSeconds(1));
        return results.get(0);
    }

    TransferResult withdraw(ItemKey item, Amount amount) {
        List<TransferResult> received = new ArrayList<>();
        service.withdraw(PLAYER, item, amount, received::add);
        scheduler.runAll();
        assertThat(received).hasSize(1);
        advance(Duration.ofSeconds(1));
        return received.get(0);
    }

    Bank bank(EntityRef owner) {
        return repository.direct().findBank(BankId.forOwner(owner)).orElseThrow();
    }

    long stock(EntityRef owner, ItemKey item) {
        return repository.direct().findBank(BankId.forOwner(owner)).map(bank -> bank.line(item).quantity())
                .orElse(0L);
    }

    private final class IslandProvider implements OwnershipProvider {

        @Override
        public String entityType() {
            return "island";
        }

        @Override
        public List<EntityMembership> resolve(UUID playerId) {
            assertThat(ManualScheduler.context()).isEqualTo(ManualScheduler.Context.PLAYER);
            return islandMembers.getOrDefault(playerId, List.of());
        }

        @Override
        public Optional<String> displayName(EntityRef entity) {
            return Optional.of("Ile " + entity.id());
        }

        @Override
        public Optional<Boolean> allows(UUID playerId, EntityRef entity,
                                        dev.novadev.novabank.api.operation.OperationType type) {
            assertThat(ManualScheduler.context()).isEqualTo(ManualScheduler.Context.PLAYER);
            return Optional.ofNullable(hostRights.get(type));
        }
    }
}
