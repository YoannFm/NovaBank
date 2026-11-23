package dev.novadev.novabank.bukkit.command;

import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.core.application.Amount;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Lecture des arguments, confirmations de reset et stock connu.
 */
class SmallCommandPartsTest {

    @Test
    void lecture_des_items_quantites_et_pages() {
        assertThat(CommandArguments.item("STONE")).contains(ItemKey.minecraft("stone"));
        assertThat(CommandArguments.item("nexo:Ruby_Block")).contains(ItemKey.parse("nexo:ruby_block"));
        assertThat(CommandArguments.item("pas valide")).isEmpty();
        assertThat(CommandArguments.amount("ALL")).contains(Amount.everything());
        assertThat(CommandArguments.amount("12")).contains(Amount.of(12));
        assertThat(CommandArguments.amount("0")).isEmpty();
        assertThat(CommandArguments.amount("1e3")).isEmpty();
        assertThat(CommandArguments.page("2")).contains(2);
        assertThat(CommandArguments.page("0")).isEmpty();
        assertThat(CommandArguments.page("x")).isEmpty();
        assertThat(CommandArguments.shortName(ItemKey.minecraft("stone"))).isEqualTo("stone");
        assertThat(CommandArguments.shortName(ItemKey.parse("nexo:ruby"))).isEqualTo("nexo:ruby");
        assertThat(CommandArguments.matching(List.of("b", "a", "B", "a"), "")).containsExactly("B", "a", "b");
    }

    @Test
    void un_reset_se_confirme_dans_le_delai_une_seule_fois_et_pour_le_meme_joueur() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.EPOCH);
        PendingConfirmations<UUID> confirmations = new PendingConfirmations<>(clock(now));
        UUID target = UUID.randomUUID();

        assertThat(confirmations.confirm("Admin", target)).isFalse();
        confirmations.request("Admin", target);
        assertThat(confirmations.confirm("Autre", target)).isFalse();
        assertThat(confirmations.confirm("Admin", UUID.randomUUID())).isFalse();
        now.set(Instant.EPOCH.plus(PendingConfirmations.WINDOW));
        assertThat(confirmations.confirm("Admin", target)).isTrue();
        assertThat(confirmations.confirm("Admin", target)).isFalse();

        confirmations.request("Admin", target);
        now.set(now.get().plus(PendingConfirmations.WINDOW).plus(Duration.ofMillis(1)));
        assertThat(confirmations.confirm("Admin", target)).isFalse();
    }

    @Test
    void stock_connu_rempli_complete_puis_oublie() {
        KnownStock stock = new KnownStock();
        UUID player = UUID.randomUUID();

        stock.replace(player, List.of(ItemKey.minecraft("stone")));
        stock.add(player, ItemKey.minecraft("dirt"));

        assertThat(stock.of(player)).containsExactlyInAnyOrder(ItemKey.minecraft("stone"), ItemKey.minecraft("dirt"));
        stock.forget(player);
        assertThat(stock.of(player)).isEmpty();
    }

    private static Clock clock(AtomicReference<Instant> now) {
        return new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now.get();
            }
        };
    }
}
