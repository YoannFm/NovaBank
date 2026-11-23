package dev.novadev.novabank.bukkit.integration;

import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.ranking.LeaderboardLine;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class PlaceholderValuesTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final EntityRef OWNER = new EntityRef("island", "i1");

    private final PlaceholderValues values = new PlaceholderValues(() -> "player",
            (player, type) -> type.equals("island") ? Optional.of(new BigDecimal("12.50")) : Optional.empty(),
            (type, limit) -> List.of(new LeaderboardLine(1, BankId.forOwner(OWNER), OWNER, new BigDecimal("1000"))),
            entity -> "Les Pirates");

    @Test
    void valeur_du_joueur_par_type_et_par_defaut() {
        assertThat(values.resolve(PLAYER, "value_island")).isEqualTo("12.5");
        assertThat(values.resolve(PLAYER, "VALUE")).isEqualTo("0");
        assertThat(values.resolve(null, "value_island")).isEqualTo("0");
    }

    @Test
    void classement_nom_valeur_et_rang_absent() {
        assertThat(values.resolve(PLAYER, "top_island_1_name")).isEqualTo("Les Pirates");
        assertThat(values.resolve(PLAYER, "top_island_1_value")).isEqualTo("1000");
        assertThat(values.resolve(PLAYER, "top_island_2_name")).isEqualTo("-");
        assertThat(values.resolve(PLAYER, "top_island_2_value")).isEqualTo("0");
        assertThat(values.resolve(PLAYER, "top_island_0_name")).isNull();
        assertThat(values.resolve(PLAYER, "inconnu")).isNull();
    }

    @Test
    void le_cache_ne_bloque_jamais_relit_en_arriere_plan_et_expire() {
        AtomicLong now = new AtomicLong();
        CompletableFuture<Map<String, BigDecimal>> pending = new CompletableFuture<>();
        int[] loads = {0};
        PlayerBankValues cache = new PlayerBankValues(player -> {
            loads[0]++;
            return loads[0] == 1 ? pending : CompletableFuture.completedFuture(Map.of("player", new BigDecimal("9")));
        }, now::get, 1000);

        assertThat(cache.value(PLAYER, "player")).isEmpty();
        assertThat(cache.value(PLAYER, "player")).isEmpty();
        assertThat(loads[0]).isEqualTo(1);

        pending.complete(Map.of("player", new BigDecimal("5")));
        assertThat(cache.value(PLAYER, "player")).contains(new BigDecimal("5"));
        assertThat(loads[0]).isEqualTo(1);

        now.set(2000);
        assertThat(cache.value(PLAYER, "player")).contains(new BigDecimal("5"));
        assertThat(cache.value(PLAYER, "player")).contains(new BigDecimal("9"));
    }
}
