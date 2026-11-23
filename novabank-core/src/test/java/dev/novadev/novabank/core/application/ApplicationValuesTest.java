package dev.novadev.novabank.core.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.api.operation.OperationStatus;
import dev.novadev.novabank.api.operation.OperationType;
import dev.novadev.novabank.api.valuation.ValuationMode;
import dev.novadev.novabank.core.access.RoleRights;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Valeurs de la couche application : quantites, choix d'item, regles, resultats, verrou.
 */
class ApplicationValuesTest {

    @Test
    void quantite_exacte_ou_tout() {
        assertThat(Amount.of(5).resolve(100, 10)).isEqualTo(5);
        assertThat(Amount.everything().resolve(100, 10)).isEqualTo(10);
        assertThat(Amount.everything().resolve(3, 10)).isEqualTo(3);
        assertThatThrownBy(() -> Amount.of(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Amount(true, 4)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void choix_d_item_coherent() {
        assertThat(ItemChoice.of(ItemKey.minecraft("stone")).item()).isPresent();
        assertThat(ItemChoice.held().kind()).isEqualTo(ItemChoice.Kind.HELD);
        assertThatThrownBy(() -> new ItemChoice(ItemChoice.Kind.HELD, Optional.of(ItemKey.minecraft("stone"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void regles_droits_par_defaut_et_bornes() {
        TransferRules rules = new TransferRules(List.of("player"), List.of("player"), Map.of(), ValuationMode.DERIVED,
                List.of(ItemKey.minecraft("stone")), 10, Duration.ZERO, true);

        assertThat(rules.rightsFor("island")).isEqualTo(RoleRights.DEFAULT);
        assertThat(rules.accepts(ItemKey.minecraft("stone"))).isTrue();
        assertThat(rules.accepts(ItemKey.minecraft("dirt"))).isFalse();
        assertThatThrownBy(() -> new TransferRules(List.of(), List.of(), Map.of(), ValuationMode.DERIVED, List.of(), 0,
                Duration.ZERO, true)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refus_de_la_banque_traduits() {
        EntityRef bank = EntityRef.player(UUID.randomUUID());
        ItemKey stone = ItemKey.minecraft("stone");

        assertThat(TransferResult.ledgerRefusal(OperationType.WITHDRAW, bank, stone, OperationStatus.INSUFFICIENT_STOCK,
                0).status()).isEqualTo(TransferStatus.INSUFFICIENT_STOCK);
        assertThat(TransferResult.ledgerRefusal(OperationType.DEPOSIT, bank, stone, OperationStatus.QUANTITY_OVERFLOW,
                0).status()).isEqualTo(TransferStatus.REFUSED);
    }

    @Test
    void verrou_sans_delai_pour_les_livraisons() {
        PlayerOperationGate gate = new PlayerOperationGate(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        UUID player = UUID.randomUUID();

        assertThat(gate.tryLock(player)).isTrue();
        assertThat(gate.tryLock(player)).isFalse();
        assertThat(gate.tryEnter(player, Duration.ZERO).status()).isEqualTo(PlayerOperationGate.Status.BUSY);
        gate.leave(player);
        assertThat(gate.tryEnter(player, Duration.ofSeconds(1)).status())
                .isEqualTo(PlayerOperationGate.Status.ACCEPTED);
    }

    @Test
    void nouvelles_tentatives_bornees() {
        StorageRetry retry = new StorageRetry(2, Duration.ZERO);
        int[] calls = new int[1];

        Optional<String> result = retry.call("test", () -> {
            calls[0]++;
            throw new IllegalStateException("panne");
        });

        assertThat(result).isEmpty();
        assertThat(calls[0]).isEqualTo(2);
        assertThat(retry.call("test", () -> "ok")).contains("ok");
        assertThatThrownBy(() -> new StorageRetry(0, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }
}
