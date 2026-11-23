package dev.novadev.novabank.bukkit.command;

import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.api.entity.BankRole;
import dev.novadev.novabank.api.entity.EntityMembership;
import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.api.operation.OperationStatus;
import dev.novadev.novabank.api.operation.OperationType;
import dev.novadev.novabank.bukkit.testing.TestLanguages;
import dev.novadev.novabank.core.application.BankSnapshot;
import dev.novadev.novabank.core.application.BankView;
import dev.novadev.novabank.core.application.DeliveryReport;
import dev.novadev.novabank.core.application.QueryStatus;
import dev.novadev.novabank.core.application.SelectionResult;
import dev.novadev.novabank.core.application.TransferResult;
import dev.novadev.novabank.core.application.TransferStatus;
import dev.novadev.novabank.core.delivery.UndeliveredPolicy;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bukkit.ChatColor;
import org.junit.jupiter.api.Test;

class TransferMessagesTest {

    private static final EntityRef BANK = new EntityRef("island", "42");
    private static final ItemKey STONE = ItemKey.minecraft("stone");

    private final TransferMessages messages = messages(UndeliveredPolicy.PENDING_DELIVERY);

    @Test
    void depot_reussi_et_refus_avec_livraison_differee() {
        List<String> lines = plain(messages.deposit(List.of(
                TransferResult.moved(TransferStatus.SUCCESS, OperationType.DEPOSIT, BANK, STONE, 1500, 0,
                        new BigDecimal("1500"), new BigDecimal("2000.5")),
                TransferResult.moved(TransferStatus.STORAGE_ERROR, OperationType.DEPOSIT, BANK, STONE, 0, 7,
                        BigDecimal.ZERO, null))));

        assertThat(lines).containsExactly(
                "NovaBank » Depot de 1 500 Pierre dans Ile 42 : +1 500 (valeur : 2 000,5).",
                "NovaBank » Base de donnees indisponible : rien n'a ete enregistre, vos items vous ont ete rendus.",
                "NovaBank » 7 Pierre n'ont pas pu vous etre rendus (inventaire plein) : ils vous seront livres a votre"
                        + " prochaine connexion.");
    }

    @Test
    void retrait_partiel_selon_la_politique() {
        TransferResult partial = TransferResult.moved(TransferStatus.SUCCESS, OperationType.WITHDRAW, BANK, STONE, 10,
                5, new BigDecimal("-10"), new BigDecimal("3"));

        assertThat(plain(messages.withdraw(partial))).hasSize(2).last().asString().contains("prochaine connexion");
        assertThat(plain(messages(UndeliveredPolicy.RETURN_TO_BANK).withdraw(partial))).last().asString()
                .contains("restes dans la banque");
    }

    @Test
    void refus_traduits_avec_leurs_placeholders() {
        assertThat(plain(messages.withdraw(TransferResult.cooldown(OperationType.WITHDRAW, Duration.ofMillis(1200)))))
                .containsExactly("NovaBank » Patientez encore 2 s avant une nouvelle operation.");
        assertThat(plain(messages.withdraw(TransferResult.refused(OperationType.WITHDRAW,
                TransferStatus.QUANTITY_LIMIT, BANK, STONE)))).containsExactly(
                "NovaBank » Au plus 2 304 unites par operation.");
        assertThat(plain(messages.withdraw(TransferResult.ledgerRefusal(OperationType.WITHDRAW, BANK, STONE,
                OperationStatus.CONCURRENT_MODIFICATION, 0)))).singleElement().asString()
                .contains("CONCURRENT_MODIFICATION");
        for (TransferStatus status : TransferStatus.values()) {
            if (status != TransferStatus.SUCCESS) {
                assertThat(plain(messages.withdraw(TransferResult.refused(OperationType.WITHDRAW, status))))
                        .allSatisfy(line -> assertThat(line).doesNotContain("bank.refusal"));
            }
        }
    }

    @Test
    void consultation_choix_et_livraisons() {
        Map<ItemKey, Long> stock = new LinkedHashMap<>();
        stock.put(STONE, 64L);
        EntityMembership island = new EntityMembership(BANK, BankRole.OWNER);
        EntityMembership personal = new EntityMembership(new EntityRef("player", "yo"), BankRole.OWNER);
        BankView view = new BankView(QueryStatus.OK, Optional.of(island), List.of(personal, island),
                Optional.of(new BankSnapshot(BANK, true, new BigDecimal("6.4"), stock, Map.of())));

        assertThat(plain(messages.info(view, "bank"))).containsExactly("NovaBank » Banque active : Ile 42 (island)",
                "Valeur : 6,4", "Stock :", " - Pierre : 64", "Autres banques : yo (player) (/bank select <type>)");
        assertThat(plain(messages.info(new BankView(QueryStatus.NO_BANK, Optional.empty(), List.of(),
                Optional.empty()), "bank"))).singleElement().asString().contains("aucune banque");
        assertThat(ChatColor.stripColor(messages.select(new SelectionResult(QueryStatus.UNKNOWN_TYPE,
                Optional.empty()), "town", List.of("player", "island"))))
                .isEqualTo("NovaBank » Type de banque inconnu : town. Types disponibles : player, island.");
        assertThat(plain(messages.delivery(new DeliveryReport(3, 2)))).hasSize(2);
        assertThat(messages.delivery(DeliveryReport.NOTHING)).isEmpty();
    }

    private static TransferMessages messages(UndeliveredPolicy policy) {
        return new TransferMessages(TestLanguages.french(), key -> key.equals(STONE) ? "Pierre" : key.id(),
                entity -> entity.type().equals("island") ? "Ile " + entity.id() : entity.id(), policy, 2304);
    }

    private static List<String> plain(List<String> lines) {
        return lines.stream().map(ChatColor::stripColor).toList();
    }
}
