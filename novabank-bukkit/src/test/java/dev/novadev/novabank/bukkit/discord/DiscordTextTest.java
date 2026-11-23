package dev.novadev.novabank.bukkit.discord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;

import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.event.BankOperationEvent;
import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.api.OperationId;
import dev.novadev.novabank.api.operation.OperationType;
import dev.novadev.novabank.bukkit.discord.DiscordText.Announce;
import dev.novadev.novabank.bukkit.discord.DiscordText.Embed;
import dev.novadev.novabank.bukkit.discord.DiscordText.Group;
import dev.novadev.novabank.config.model.DiscordSettings;
import dev.novadev.novabank.config.model.Secret;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DiscordTextTest {

    @Test
    void noms_du_jeu_sans_mention_ni_markdown() {
        assertThat(DiscordText.escape("@everyone **riche**")).isEqualTo("@​everyone \\*\\*riche\\*\\*");
    }

    @Test
    void json_echappe_et_mentions_coupees() {
        assertThat(DiscordText.json(List.of(new Embed("a \"b\"\nc\\d", DiscordText.GREEN)))).isEqualTo(
                "{\"embeds\":[{\"description\":\"a \\\"b\\\"\\nc\\\\d\",\"color\":3066993}],"
                        + "\"allowed_mentions\":{\"parse\":[]}}");
    }

    @Test
    void encadres_repartis_sans_depasser_les_limites() {
        Embed big = new Embed("x".repeat(3000), DiscordText.RED);
        assertThat(DiscordText.payloads(List.of(big, big, new Embed("court", DiscordText.RED)))).hasSize(2);
        assertThat(DiscordText.payloads(Collections.nCopies(25, new Embed("a", DiscordText.GREEN)))).hasSize(3);
        assertThat(DiscordText.payloads(List.of(new Embed("y".repeat(5000), DiscordText.GREEN))).get(0))
                .contains("y".repeat(DiscordText.MAX_DESCRIPTION) + "\"");
        assertThat(DiscordText.payloads(List.of())).isEmpty();
    }

    @Test
    void operations_d_un_joueur_sur_une_banque_regroupees() {
        List<Group> groups = DiscordText.group(List.of(
                new Announce(OperationType.DEPOSIT, "Steve", "Pins", "stone", 64, BigDecimal.ONE),
                new Announce(OperationType.DEPOSIT, "Alex", "Pins", "stone", 1, BigDecimal.ONE),
                new Announce(OperationType.DEPOSIT, "Steve", "Pins", "iron_block", 12, BigDecimal.TEN),
                new Announce(OperationType.DEPOSIT, "Steve", "Pins", "stone", 64, BigDecimal.ONE),
                new Announce(OperationType.WITHDRAW, "Steve", "Pins", "stone", 5, BigDecimal.ONE)));
        assertThat(groups).extracting(Group::player, Group::type).containsExactly(
                tuple("Steve", OperationType.DEPOSIT), tuple("Alex", OperationType.DEPOSIT),
                tuple("Steve", OperationType.WITHDRAW));
        assertThat(groups.get(0).items()).containsExactly(entry("stone", 128L), entry("iron_block", 12L));
        assertThat(groups.get(0).value()).isEqualByComparingTo("12");
    }

    @Test
    void aide_selon_le_refus_de_discord() {
        assertThat(DiscordFeed.hint(401)).isEqualTo("unauthorized");
        assertThat(DiscordFeed.hint(404)).isEqualTo("not-found");
        assertThat(DiscordFeed.hint(500)).isEqualTo("other");
    }

    @Test
    void delai_de_la_limite_de_debit() {
        assertThat(DiscordText.retryAfter("{\"message\": \"You are being rate limited.\", \"retry_after\": 1.5}"))
                .isEqualTo(1.5);
        assertThat(DiscordText.retryAfter("illisible")).isEqualTo(5.0);
    }

    @Test
    void seules_les_operations_soldees_choisies_sont_annoncees() {
        DiscordSettings settings = new DiscordSettings(true, DiscordSettings.Mode.WEBHOOK, Secret.of("u"), Secret.of(""), "",
                Set.of(OperationType.DEPOSIT, OperationType.WITHDRAW), List.of("island"), 10, 5);
        EntityRef island = new EntityRef("island", "1");
        assertThat(DiscordFeed.announced(settings, event(island, OperationType.DEPOSIT, "COMMITTED", 64))).isTrue();
        assertThat(DiscordFeed.announced(settings, event(island, OperationType.WITHDRAW, "RESERVED", 64))).isFalse();
        assertThat(DiscordFeed.announced(settings, event(island, OperationType.DEPOSIT, "COMMITTED", 5))).isFalse();
        assertThat(DiscordFeed.announced(settings, event(island, OperationType.RESET, "COMMITTED", 64))).isFalse();
        assertThat(DiscordFeed.announced(settings, event(new EntityRef("player", "p"), OperationType.DEPOSIT,
                "COMMITTED", 64))).isFalse();
    }

    private static BankOperationEvent event(EntityRef owner, OperationType type, String state, long quantity) {
        return new BankOperationEvent(OperationId.random(), BankId.forOwner(owner), owner, type, state,
                ItemKey.minecraft("stone"), quantity, BigDecimal.ONE, 1);
    }
}
