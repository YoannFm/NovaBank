package dev.novadev.novabank.bukkit.command;

import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.api.BankId;
import dev.novadev.novabank.api.OperationId;
import dev.novadev.novabank.api.entity.EntityRef;
import dev.novadev.novabank.api.item.ItemKey;
import dev.novadev.novabank.api.valuation.ValuationMode;
import dev.novadev.novabank.bukkit.testing.TestLanguages;
import dev.novadev.novabank.core.journal.OperationRecord;
import dev.novadev.novabank.core.ranking.LeaderboardService;
import dev.novadev.novabank.core.repository.MutationDecision;
import dev.novadev.novabank.core.testing.InMemoryStorageBackend;
import dev.novadev.novabank.core.testing.ManualScheduler;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class LeaderboardCommandTest {

    private static final ItemKey STONE = ItemKey.minecraft("stone");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-28T12:00:00Z"), ZoneOffset.UTC);

    private final InMemoryStorageBackend storage = new InMemoryStorageBackend();
    private final RecordingActor viewer = new RecordingActor(false);
    private final Map<String, Integer> sizes = new LinkedHashMap<>(Map.of("player", 2));
    private final LeaderboardService service;
    private final LeaderboardCommand command;

    LeaderboardCommandTest() {
        Logger quiet = Logger.getLogger("leaderboard-command-test");
        quiet.setLevel(Level.OFF);
        service = new LeaderboardService(storage.banks(), storage.content(),
                (bank, category) -> BigDecimal.valueOf(bank.line(STONE).quantity()), () -> sizes, CLOCK, quiet);
        command = new LeaderboardCommand(service, () -> sizes, TestLanguages::french, entity -> "joueur-" + entity.id());
    }

    private UUID player(String suffix, long stones) {
        UUID id = UUID.nameUUIDFromBytes(suffix.getBytes());
        EntityRef owner = EntityRef.player(id);
        BankId bank = BankId.forOwner(owner);
        storage.banks().createBank(bank, owner, ValuationMode.DERIVED);
        OperationId op = OperationId.random();
        storage.banks().apply(op, bank, current -> MutationDecision.accept(
                current.withLine(STONE, current.line(STONE).plus(stones)),
                OperationRecord.deposit(op, bank, STONE, stones, stones, BigDecimal.valueOf(stones), CLOCK.instant())));
        return id;
    }

    @Test
    void les_meilleurs_sont_listes_et_le_joueur_hors_du_top_voit_son_rang() {
        player("a", 30);
        player("b", 20);
        UUID last = player("c", 5);
        service.rebuild();

        command.show(viewer, last, null);

        assertThat(viewer.received()).hasSize(4);
        assertThat(viewer.received().get(0)).contains("Classement player", "top 2");
        assertThat(viewer.received().get(1)).contains("#1").contains("30");
        assertThat(viewer.received().get(2)).contains("#2");
        assertThat(viewer.received().get(3)).contains("Votre rang", "#3");
    }

    @Test
    void le_joueur_dans_le_top_est_mis_en_avant_sans_ligne_supplementaire() {
        UUID top = player("a", 30);
        service.rebuild();

        command.show(viewer, top, "player");

        assertThat(viewer.received()).hasSize(2);
        assertThat(viewer.received().get(1)).contains("(vous)");
    }

    @Test
    void classement_vide_type_inconnu_et_classements_desactives() {
        UUID nobody = UUID.randomUUID();
        service.rebuild();

        command.show(viewer, nobody, "player");
        command.show(viewer, nobody, "island");
        LeaderboardCommand.DISABLED.show(viewer, nobody, null);

        assertThat(viewer.received()).anySatisfy(line -> assertThat(line).contains("Aucune banque classee"));
        assertThat(viewer.received()).anySatisfy(line -> assertThat(line).contains("Votre banque n'est pas encore classee"));
        assertThat(viewer.received()).anySatisfy(line -> assertThat(line).contains("Classement inconnu : island"));
        assertThat(viewer.received()).last().satisfies(line -> assertThat(line).contains("disabled"));
    }
}
