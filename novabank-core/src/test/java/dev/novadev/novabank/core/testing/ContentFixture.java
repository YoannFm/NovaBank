package dev.novadev.novabank.core.testing;

import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.api.OperationId;
import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.api.operation.OperationStatus;
import dev.novadev.novabank.api.valuation.ValuationMode;
import dev.novadev.novabank.core.delivery.PendingDelivery;
import dev.novadev.novabank.core.delivery.UndeliveredPolicy;
import dev.novadev.novabank.core.ledger.BankLedger;
import dev.novadev.novabank.core.storage.StorageBackend;
import dev.novadev.novabank.core.storage.StorageContent;
import dev.novadev.novabank.core.storage.StoredDelivery;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Contenu de reference pour les tests de contenu et de migration : deux banques (une par mode
 * de valorisation), depots, retraits livres, rendus, partiellement livres et laisses reserves,
 * reset, livraison en attente partiellement remise, livraison entierement remise, banques
 * actives, montants a quatre decimales.
 */
public final class ContentFixture {

    /** Item a prix decimal. */
    public static final ItemKey STONE = ItemKey.minecraft("stone");
    /** Item custom. */
    public static final ItemKey RUBY = ItemKey.parse("nexo:ruby_block");

    private ContentFixture() {
    }

    /**
     * Remplit un backend vide par le service metier.
     *
     * @param backend backend vide
     * @return l'identifiant d'un retrait laisse reserve (a solder apres copie)
     */
    public static OperationId populate(StorageBackend backend) {
        MutablePriceCatalog catalog = new MutablePriceCatalog().price(STONE, "1.2345").price(RUBY, "12.3456");
        BankLedger ledger = new BankLedger(backend.banks(), catalog, backend.deliveries(),
                UndeliveredPolicy.PENDING_DELIVERY, Clock.systemUTC());
        UUID player = UUID.fromString("00000000-0000-0000-0000-00000000c0de");
        BankId accumulated = BankId.forOwner(EntityRef.player(player));
        BankId derived = BankId.forOwner(new EntityRef(EntityRef.FACTION, "Les Batisseurs \"du\" Nord"));
        ledger.openBank(accumulated, EntityRef.player(player), ValuationMode.ACCUMULATED);
        ledger.openBank(derived, new EntityRef(EntityRef.FACTION, "Les Batisseurs \"du\" Nord"),
                ValuationMode.DERIVED);
        for (BankId bank : List.of(accumulated, derived)) {
            require(ledger.deposit(OperationId.random(), bank, STONE, 640L).status());
            require(ledger.deposit(OperationId.random(), bank, RUBY, 37L).status());
            OperationId delivered = OperationId.random();
            require(ledger.reserveWithdrawal(delivered, bank, STONE, 64L, player).status());
            require(ledger.confirmWithdrawal(delivered, 64L).status());
            OperationId partial = OperationId.random();
            require(ledger.reserveWithdrawal(partial, bank, RUBY, 9L, player).status());
            require(ledger.confirmWithdrawal(partial, 4L).status());
            OperationId returned = OperationId.random();
            require(ledger.reserveWithdrawal(returned, bank, STONE, 10L, player).status());
            require(ledger.cancelWithdrawal(returned).status());
        }
        require(ledger.reset(OperationId.random(), accumulated).status());
        require(ledger.deposit(OperationId.random(), accumulated, STONE, 3L).status());
        OperationId full = OperationId.random();
        backend.deliveries().add(new PendingDelivery(full, player, STONE, 5L));
        assertThat(backend.deliveries().markDelivered(full, 5L)).isTrue();
        List<PendingDelivery> pending = backend.deliveries().pendingFor(player);
        assertThat(backend.deliveries().markDelivered(pending.get(0).operationId(), 2L)).isTrue();
        backend.activeBanks().save(player, new EntityRef(EntityRef.FACTION, "Les Batisseurs \"du\" Nord"));
        backend.activeBanks().save(UUID.randomUUID(), new EntityRef(EntityRef.ISLAND, "ile-42"));
        OperationId reserved = OperationId.random();
        require(ledger.reserveWithdrawal(reserved, derived, STONE, 7L, player).status());
        return reserved;
    }

    /**
     * Verifie que deux contenus sont identiques element par element.
     *
     * @param expected contenu de reference
     * @param actual   contenu compare
     */
    public static void assertSameContent(StorageContent expected, StorageContent actual) {
        assertThat(byKey(actual)).isEqualTo(byKey(expected));
        assertThat(deliveries(actual)).as("livraisons").isEqualTo(deliveries(expected));
    }

    /**
     * Livraisons dans l'ordre de lecture, qui doit suivre l'ordre d'enregistrement (dates
     * croissantes), puis triees par date et operation : deux livraisons enregistrees dans la meme
     * milliseconde peuvent etre relues dans un ordre different selon le backend.
     */
    private static List<StoredDelivery> deliveries(StorageContent content) {
        List<StoredDelivery> deliveries = new ArrayList<>();
        content.exportDeliveries(deliveries::add);
        assertThat(deliveries).as("livraisons lues par ordre d'enregistrement")
                .isSortedAccordingTo(Comparator.comparing(StoredDelivery::createdAt));
        deliveries.sort(Comparator.comparing(StoredDelivery::createdAt)
                .thenComparing(stored -> stored.delivery().operationId().toString()));
        return deliveries;
    }

    private static Map<String, Object> byKey(StorageContent content) {
        Map<String, Object> elements = new TreeMap<>();
        content.exportBanks(bank -> elements.put("banque " + bank.id(), bank));
        content.exportOperations(record -> elements.put("operation " + record.operationId(), record));
        content.exportActiveBanks(choice -> elements.put("active " + choice.playerId(), choice));
        return elements;
    }

    private static void require(OperationStatus status) {
        assertThat(status).isEqualTo(OperationStatus.SUCCESS);
    }
}
